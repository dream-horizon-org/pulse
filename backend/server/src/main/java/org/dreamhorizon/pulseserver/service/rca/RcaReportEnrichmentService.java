package org.dreamhorizon.pulseserver.service.rca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.SessionEvidenceService;
import org.dreamhorizon.pulseserver.service.rootcause.models.EvidenceSession;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.service.rootcause.models.SessionEvidenceResult;

/**
 * Builds the RCA POST body with {@code rootCausePayload} and per-segment session evidence (same
 * pipeline as the legacy synchronous {@code RcaReportProxyHandler} path).
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RcaReportEnrichmentService {

  private static final String REGENERATE_FIELD = "regenerate";
  private static final String ROOT_CAUSE_PAYLOAD_FIELD = "rootCausePayload";

  private final ObjectMapper objectMapper;
  private final RootCauseService rootCauseService;
  private final SessionEvidenceService sessionEvidenceService;

  /**
   * Enriches the RCA JSON body with root-cause data and example sessions.
   *
   * @param forceRootCauseRefresh forwarded to {@link RootCauseService#getRootCause}
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

              JsonNode resultNode = objectMapper.valueToTree(rootCauseResult);
              working.set(ROOT_CAUSE_PAYLOAD_FIELD, resultNode);

              List<RootCauseSegment> segments = rootCauseResult.getSegments();

              if (segments.isEmpty()) {
                return serializeEnrichedBody(working, rootCauseResult, date, windowEndExclusive)
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
                return serializeEnrichedBody(working, rootCauseResult, date, windowEndExclusive)
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
                            working, rootCauseResult, date, windowEndExclusive);
                      })
                  .onErrorReturnItem(
                      new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
            })
        .onErrorReturnItem(
            new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
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
      ObjectNode working, RootCauseResult rootCauseResult, LocalDate date, Instant windowEnd) {
    return Single.fromCallable(
        () -> {
          String enrichedBody = objectMapper.writeValueAsString(working);
          return new RcaEnrichmentOutcome(enrichedBody, rootCauseResult, date, windowEnd, true);
        });
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
