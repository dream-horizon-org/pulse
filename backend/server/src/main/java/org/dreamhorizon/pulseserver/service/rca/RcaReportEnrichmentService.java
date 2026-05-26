package org.dreamhorizon.pulseserver.service.rca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionRcaDrillSignals;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionRestResponseMapper;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionService;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.ScreenRcaService;
import org.dreamhorizon.pulseserver.service.rootcause.SessionEvidenceService;
import org.dreamhorizon.pulseserver.service.rootcause.models.EvidenceSession;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.util.NumberCoercionUtils;

/**
 * Builds the RCA POST body with {@code rootCausePayload}, optional {@code errorAttributionPayload},
 * and per-segment session evidence (same pipeline as the legacy synchronous {@code
 * RcaReportProxyHandler} path).
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RcaReportEnrichmentService {

  private static final int SCREEN_V2_LOOKBACK_DAYS = 7;
  private static final String REGENERATE_FIELD = "regenerate";
  private static final String ROOT_CAUSE_PAYLOAD_FIELD = "rootCausePayload";
  private static final String ERROR_ATTRIBUTION_PAYLOAD_FIELD = "errorAttributionPayload";
  private static final String START_FIELD = "start";
  private static final String END_FIELD = "end";

  private final ObjectMapper objectMapper;
  private final RootCauseService rootCauseService;
  private final ScreenRcaService screenRcaService;
  private final SessionEvidenceService sessionEvidenceService;
  private final ErrorAttributionService errorAttributionService;
  private final RootCauseConfig rootCauseConfig;

  /**
   * Enriches the RCA JSON body with root-cause data and example sessions.
   *
   * @param forceRootCauseRefresh forwarded to {@link RootCauseService#getRootCause} (interaction) or
   *     {@link ScreenRcaService#getScreenRootCause} (screen)
   */
  public CompletionStage<RcaEnrichmentOutcome> enrichAsync(
      RcaParsedReportBody parsed, boolean forceRootCauseRefresh) {
    return enrichInternal(parsed, forceRootCauseRefresh).toCompletionStage();
  }

  private Single<RcaEnrichmentOutcome> enrichInternal(
      RcaParsedReportBody parsed, boolean forceRootCauseRefresh) {
    ObjectNode working = parsed.bodyRoot().deepCopy();
    working.remove(REGENERATE_FIELD);
    String fallbackBody = parsed.rawBody();
    String projectId = parsed.projectId();
    RcaType type = parsed.entityType();
    String entityKey = parsed.entityKey();
    LocalDate date = parsed.date();
    Instant windowEndExclusive = Instant.now();

    if (type == RcaType.SCREEN) {
      return enrichScreenAsync(parsed, forceRootCauseRefresh);
    }

    if (type == RcaType.SCREEN_V2) {
      return enrichScreenV2Async(parsed);
    }

    // For now, only INTERACTION type is fully supported with enrichment
    // Other types may have different enrichment paths in the future
    // TODO: Introduce strategy-style abstraction (interface + implementations) for extensibility
    if (type != RcaType.INTERACTION) {
      return Single.just(
          new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
    }

    return rootCauseService
        .getRootCause(projectId, entityKey, date, windowEndExclusive, forceRootCauseRefresh)
        .flatMap(
            rootCauseResult -> {
              if (rootCauseResult.getSegments() == null) {
                log.warn("Root cause result has no segments");
                return Single.just(
                    new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
              }

              RootCauseResult sanitizedResult = sanitizeForAiReport(rootCauseResult);
              JsonNode resultNode = objectMapper.valueToTree(sanitizedResult);
              working.set(ROOT_CAUSE_PAYLOAD_FIELD, resultNode);

              List<RootCauseSegment> segments = sanitizedResult.getSegments();

              if (segments.isEmpty()) {
                return serializeEnrichedBody(
                        working, rootCauseResult, date, windowEndExclusive, projectId, entityKey)
                    .onErrorReturnItem(
                        new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
              }

              // Skip session evidence queries if root cause was cached (already has exampleSessionIds)
              boolean isCachedFromStore = rootCauseResult.getCachedAt() != null;
              boolean allSegmentsHaveSessions =
                  segments.stream()
                      .allMatch(
                          s -> s.getExampleSessionIds() != null && !s.getExampleSessionIds().isEmpty());
              boolean skipSessionEvidence = isCachedFromStore && allSegmentsHaveSessions;

              if (skipSessionEvidence) {
                return serializeEnrichedBody(
                        working, rootCauseResult, date, windowEndExclusive, projectId, entityKey)
                    .onErrorReturnItem(
                        new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
              }

              return fetchSessionEvidenceForAllSegments(segments, projectId, entityKey, date)
                  .flatMap(
                      enrichedSegments -> {
                        JsonNode rcPayloadNode = working.get(ROOT_CAUSE_PAYLOAD_FIELD);
                        if (rcPayloadNode instanceof ObjectNode rcPayload) {
                          rcPayload.set("segments", objectMapper.valueToTree(enrichedSegments));
                        }
                        return serializeEnrichedBody(
                            working, rootCauseResult, date, windowEndExclusive, projectId, entityKey);
                      })
                  .onErrorReturnItem(
                      new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
            })
        .onErrorReturnItem(
            new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
  }

  /**
   * Screen RCA async job: recompute tabular root cause (optional cache bypass) and build the pulse_ai
   * {@code rca/screen-report} JSON body. Does not attach error-attribution (interaction-only).
   */
  private Single<RcaEnrichmentOutcome> enrichScreenAsync(
      RcaParsedReportBody parsed, boolean forceRootCauseRefresh) {
    String fallbackBody = parsed.rawBody();
    String projectId = parsed.projectId();
    String screenName = parsed.entityKey();
    LocalDate anchorDate = parsed.date();
    ObjectNode root = parsed.bodyRoot();
    JsonNode startNode = root.get(START_FIELD);
    JsonNode endNode = root.get(END_FIELD);
    if (startNode == null
        || endNode == null
        || !startNode.isTextual()
        || !endNode.isTextual()
        || startNode.asText().isBlank()
        || endNode.asText().isBlank()) {
      return Single.just(
          new RcaEnrichmentOutcome(fallbackBody, null, anchorDate, Instant.now(), false));
    }
    final Instant windowStartInclusive;
    final Instant windowEndExclusive;
    try {
      windowStartInclusive = Instant.parse(startNode.asText().trim());
      windowEndExclusive = Instant.parse(endNode.asText().trim());
    } catch (DateTimeParseException e) {
      return Single.just(
          new RcaEnrichmentOutcome(fallbackBody, null, anchorDate, Instant.now(), false));
    }
    return screenRcaService
        .getScreenRootCause(
            projectId, screenName, anchorDate, windowEndExclusive, forceRootCauseRefresh)
        .map(
            screenResult -> {
              try {
                ObjectNode aiBody = objectMapper.createObjectNode();
                aiBody.put("screenName", screenName);
                aiBody.put("date", anchorDate.toString());
                aiBody.put("start", windowStartInclusive.toString());
                aiBody.put("end", windowEndExclusive.toString());
                aiBody.set("rootCausePayload", objectMapper.valueToTree(screenResult));
                String body = objectMapper.writeValueAsString(aiBody);
                return new RcaEnrichmentOutcome(
                    body, screenResult, anchorDate, windowEndExclusive, true);
              } catch (Exception e) {
                log.warn("Screen RCA enrichment serialize failed: {}", e.getMessage());
                return new RcaEnrichmentOutcome(
                    fallbackBody, null, anchorDate, windowEndExclusive, false);
              }
            })
        .onErrorReturnItem(
            new RcaEnrichmentOutcome(fallbackBody, null, anchorDate, windowEndExclusive, false));
  }

  /**
   * Screen RCA v2 async job: fetches ranked problems + evidences from ClickHouse and builds the
   * pulse_ai {@code rca/screen-report/v2} JSON body. No error-attribution or session enrichment.
   */
  private Single<RcaEnrichmentOutcome> enrichScreenV2Async(RcaParsedReportBody parsed) {
    String fallbackBody = parsed.rawBody();
    String projectId = parsed.projectId();
    String screenName = parsed.entityKey();
    LocalDate anchorDate = parsed.date();
    ObjectNode root = parsed.bodyRoot();
    JsonNode startNode = root.get(START_FIELD);
    JsonNode endNode = root.get(END_FIELD);
    if (startNode == null
        || endNode == null
        || !startNode.isTextual()
        || !endNode.isTextual()
        || startNode.asText().isBlank()
        || endNode.asText().isBlank()) {
      return Single.just(
          new RcaEnrichmentOutcome(fallbackBody, null, anchorDate, Instant.now(), false));
    }
    final Instant windowStartInclusive;
    final Instant windowEndExclusive;
    try {
      windowStartInclusive = Instant.parse(startNode.asText().trim());
      windowEndExclusive = Instant.parse(endNode.asText().trim());
    } catch (DateTimeParseException e) {
      return Single.just(
          new RcaEnrichmentOutcome(fallbackBody, null, anchorDate, Instant.now(), false));
    }
    LocalDate resolvedAnchorDate = LocalDate.ofInstant(windowEndExclusive, ZoneOffset.UTC);
    RootCauseQueryBuilder.Window window =
        new RootCauseQueryBuilder.Window(
            resolvedAnchorDate, SCREEN_V2_LOOKBACK_DAYS, windowEndExclusive);
    return screenRcaService
        .getScreenRootCauseV2(projectId, screenName, window)
        .map(
            v2Result -> {
              try {
                ObjectNode aiBody = objectMapper.createObjectNode();
                aiBody.put("screenName", screenName);
                aiBody.put("start", windowStartInclusive.toString());
                aiBody.put("end", windowEndExclusive.toString());
                aiBody.set("problems", objectMapper.valueToTree(v2Result.getProblems()));
                aiBody.set("evidences", objectMapper.valueToTree(v2Result.getEvidences()));
                String body = objectMapper.writeValueAsString(aiBody);
                return new RcaEnrichmentOutcome(
                    body, null, resolvedAnchorDate, windowEndExclusive, true);
              } catch (Exception e) {
                log.warn("Screen RCA v2 enrichment serialize failed: {}", e.getMessage());
                return new RcaEnrichmentOutcome(
                    fallbackBody, null, resolvedAnchorDate, windowEndExclusive, false);
              }
            })
        .onErrorResumeNext(
            error -> {
              log.warn(
                  "Screen RCA v2 enrichment failed for project={}, screen={}: {}",
                  projectId,
                  screenName,
                  error.getMessage(),
                  error);
              return Single.just(
                  new RcaEnrichmentOutcome(
                      fallbackBody, null, resolvedAnchorDate, windowEndExclusive, false));
            });
  }

  /**
   * Fetches session evidence for all segments in parallel using Single.zip(),
   * then updates each segment with the returned session IDs.
   */
  private Single<List<RootCauseSegment>> fetchSessionEvidenceForAllSegments(
      List<RootCauseSegment> segments,
      String projectId,
      String entityKey,
      LocalDate date) {

    LocalDate lookbackStart = date.minusDays(6);
    Instant lookbackInstant = lookbackStart.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant endInstant = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

    // Create a list of Singles, one per segment
    List<Single<RootCauseSegmentWithIndex>> segmentEvidenceSingles =
        IntStream.range(0, segments.size())
            .mapToObj(
                i -> {
                  RootCauseSegment segment = segments.get(i);
                  Map<String, Double> segmentMetrics = extractSegmentMetrics(segment.getMetrics());

                  return sessionEvidenceService
                      .getSessionEvidence(
                          projectId, entityKey, lookbackInstant, endInstant,
                          segment.getDimensions(), segmentMetrics, 2)
                      .map(
                          evidenceResult -> {
                            List<String> sessionIds =
                                evidenceResult.getSessions().stream()
                                    .map(EvidenceSession::getSessionId)
                                    .collect(Collectors.toList());
                            return new RootCauseSegmentWithIndex(i, segment, sessionIds);
                          })
                      .onErrorReturnItem(
                          new RootCauseSegmentWithIndex(i, segment, List.of()));
                })
            .collect(Collectors.toList());

    // Use Single.zip to run all in parallel and collect results
    return Single.zip(
        segmentEvidenceSingles,
        results -> {
          // Create a mutable copy of segments with session IDs set
          List<RootCauseSegment> enrichedSegments =
              segments.stream()
                  .map(
                      s ->
                          RootCauseSegment.builder()
                              .label(s.getLabel())
                              .dimensions(s.getDimensions())
                              .metrics(s.getMetrics())
                              .deltas(s.getDeltas())
                              .exampleSessionIds(null)
                              .build())
                  .collect(Collectors.toList());

          for (Object result : results) {
            RootCauseSegmentWithIndex withIndex = (RootCauseSegmentWithIndex) result;
            RootCauseSegment enriched = enrichedSegments.get(withIndex.index());
            enriched.setExampleSessionIds(withIndex.sessionIds());
          }

          return enrichedSegments;
        });
  }

  private Single<RcaEnrichmentOutcome> serializeEnrichedBody(
      ObjectNode working,
      RootCauseResult rootCauseResult,
      LocalDate date,
      Instant windowEnd,
      String projectId,
      String entityKey) {
    return Single.fromCallable(
            () -> {
              fetchErrorAttributionForEnrichment(projectId, entityKey, date, windowEnd, working);
              String enrichedBody = objectMapper.writeValueAsString(working);
              return new RcaEnrichmentOutcome(enrichedBody, rootCauseResult, date, windowEnd, true);
            })
        .subscribeOn(Schedulers.io());
  }

  /**
   * Sanitizes RootCauseResult for AI report by:
   * 1. Filtering out low-volume segments (< minSegmentVolumePct% of baseline)
   * 2. Sorting remaining segments by problematic_count descending (most affected first)
   * 3. Removing internal-only fields like problematic_count from final output
   */
  private RootCauseResult sanitizeForAiReport(RootCauseResult result) {
    Map<String, Object> sanitizedBaseline = sanitizeMetrics(result.getBaseline());
    double minVolumePct = rootCauseConfig.getMinSegmentVolumePct();

    // Calculate minimum volume threshold based on baseline
    long baselineVolume = NumberCoercionUtils.toLong(result.getBaseline().get("volume"));
    double minVolumeThreshold = baselineVolume * (minVolumePct / 100.0);

    // Filter segments by volume, sort by problematic_count descending, then sanitize
    List<RootCauseSegment> filteredAndSortedSegments = result.getSegments().stream()
        .filter(s -> isSegmentVolumeAboveThreshold(s, minVolumeThreshold))
        .sorted(Comparator.comparingLong(this::getProblematicCount).reversed())
        .map(s -> RootCauseSegment.builder()
            .label(s.getLabel())
            .dimensions(s.getDimensions())
            .metrics(sanitizeMetrics(s.getMetrics()))
            .deltas(s.getDeltas())
            .exampleSessionIds(s.getExampleSessionIds())
            .build())
        .toList();

    return result.toBuilder()
        .baseline(sanitizedBaseline)
        .segments(filteredAndSortedSegments)
        .build();
  }

  private static boolean isSegmentVolumeAboveThreshold(RootCauseSegment segment, double threshold) {
    if (segment.getMetrics() == null) {
      return false;
    }
    long volume = NumberCoercionUtils.toLong(segment.getMetrics().get("volume"));
    return volume >= threshold;
  }

  private long getProblematicCount(RootCauseSegment segment) {
    if (segment.getMetrics() == null) {
      return 0L;
    }
    return NumberCoercionUtils.toLong(segment.getMetrics().get("problematic_count"));
  }

  private static Map<String, Object> sanitizeMetrics(Map<String, Object> metrics) {
    if (metrics == null) {
      return Map.of();
    }
    Map<String, Object> sanitized = new HashMap<>(metrics);
    sanitized.remove("problematic_count");
    return sanitized;
  }

  /**
   * Fetches error-attribution drill bundle for the RCA window (same {@link RootCauseQueryBuilder.Window}
   * as root cause) and attaches JSON to {@code working}. On failure, omits the payload key; enrichment
   * still completes.
   */
  private void fetchErrorAttributionForEnrichment(
      String projectId,
      String entityKey,
      LocalDate anchorDate,
      Instant windowEndExclusive,
      ObjectNode working) {
    try {
      RootCauseQueryBuilder.Window window =
          new RootCauseQueryBuilder.Window(
              anchorDate, rootCauseConfig.getLookbackDays(), windowEndExclusive);
      var bundle =
          errorAttributionService
              .getErrorAttributionWithOptionalDrillDown(
                  projectId,
                  entityKey,
                  window.startInclusive,
                  window.endExclusive,
                  ErrorAttributionRcaDrillSignals.CANONICAL_FOR_RCA)
              .subscribeOn(Schedulers.io())
              .blockingGet();
      working.set(
          ERROR_ATTRIBUTION_PAYLOAD_FIELD,
          objectMapper.valueToTree(ErrorAttributionRestResponseMapper.fromBundle(bundle)));
    } catch (Exception e) {
      log.warn("RCA enrichment error attribution failed: {}", e.getMessage());
    }
  }

  private Map<String, Double> extractSegmentMetrics(Map<String, Object> metrics) {
    Map<String, Double> result = new HashMap<>();
    if (metrics == null) {
      return result;
    }
    for (String key : List.of("error_rate", "apdex")) {
      Object val = metrics.get(key);
      if (val != null) {
        try {
          result.put(key, objectMapper.convertValue(val, Double.class));
        } catch (IllegalArgumentException e) {
          log.warn("Failed to parse {} metric: {}", key, val);
        }
      }
    }
    return result;
  }

  /**
   * Tuple to hold segment index with its evidence result for parallel processing.
   */
  private record RootCauseSegmentWithIndex(
      int index, RootCauseSegment segment, List<String> sessionIds) {}
}
