package org.dreamhorizon.pulseserver.service.rca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionRcaDrillSignals;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionRestResponseMapper;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionService;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.SessionEvidenceService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;

/**
 * Builds the RCA POST body with {@code rootCausePayload}, optional {@code errorAttributionPayload},
 * and per-segment session evidence (same pipeline as the legacy synchronous {@code
 * RcaReportProxyHandler} path).
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RcaReportEnrichmentService {

  private static final String REGENERATE_FIELD = "regenerate";
  private static final String ROOT_CAUSE_PAYLOAD_FIELD = "rootCausePayload";
  private static final String ERROR_ATTRIBUTION_PAYLOAD_FIELD = "errorAttributionPayload";

  private final ObjectMapper objectMapper;
  private final RootCauseService rootCauseService;
  private final SessionEvidenceService sessionEvidenceService;
  private final ErrorAttributionService errorAttributionService;
  private final RootCauseConfig rootCauseConfig;

  /**
   * Enriches the RCA JSON body with root-cause data and example sessions.
   *
   * @param forceRootCauseRefresh forwarded to {@link RootCauseService#getRootCause}
   */
  public CompletionStage<RcaEnrichmentOutcome> enrichAsync(
      RcaParsedReportBody parsed, boolean forceRootCauseRefresh) {
    ObjectNode working = parsed.bodyRoot().deepCopy();
    working.remove(REGENERATE_FIELD);
    String fallbackBody = parsed.rawBody();
    String projectId = parsed.projectId();
    RcaType type = parsed.type();
    String entityKey = parsed.entityKey();
    LocalDate date = parsed.date();
    Instant windowEndExclusive = Instant.now();

    CompletableFuture<RcaEnrichmentOutcome> future = new CompletableFuture<>();

    // For now, only INTERACTION type is fully supported with enrichment
    // Other types may have different enrichment paths in the future
    if (type != RcaType.INTERACTION) {
      // For non-interaction types, return the body as-is without enrichment
      // This can be extended in the future for SESSION and SCREEN types
      future.complete(
          new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
      return future;
    }

    rootCauseService
        .getRootCause(projectId, entityKey, date, windowEndExclusive, forceRootCauseRefresh)
        .subscribe(
            rootCauseResult -> {
              if (rootCauseResult.getSegments() != null) {
                try {
                  JsonNode resultNode = objectMapper.valueToTree(rootCauseResult);
                  working.set(ROOT_CAUSE_PAYLOAD_FIELD, resultNode);

                  fetchErrorAttributionForEnrichment(
                      projectId, entityKey, date, windowEndExclusive, working);

                  if (!rootCauseResult.getSegments().isEmpty()) {
                    List<RootCauseSegment> segments = rootCauseResult.getSegments();

                    // Skip session evidence queries if root cause was cached (already has exampleSessionIds)
                    boolean isCachedFromStore = rootCauseResult.getCachedAt() != null;
                    boolean allSegmentsHaveSessions =
                        segments.stream()
                            .allMatch(
                                s -> s.getExampleSessionIds() != null && !s.getExampleSessionIds().isEmpty());
                    boolean skipSessionEvidence = isCachedFromStore && allSegmentsHaveSessions;

                    if (skipSessionEvidence) {
                      String enrichedBody = objectMapper.writeValueAsString(working);
                      future.complete(
                          new RcaEnrichmentOutcome(
                              enrichedBody, rootCauseResult, date, windowEndExclusive, true));
                    } else {
                      LocalDate lookbackStart = date.minusDays(6);

                      AtomicInteger pendingQueries = new AtomicInteger(segments.size());

                      for (int i = 0; i < segments.size(); i++) {
                        RootCauseSegment segment = segments.get(i);
                        final int segmentIndex = i;
                        Map<String, Double> segmentMetrics = extractSegmentMetrics(segment.getMetrics());

                        sessionEvidenceService
                            .getSessionEvidence(
                                projectId,
                                entityKey,
                                lookbackStart.atStartOfDay().toInstant(ZoneOffset.UTC),
                                date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                                segment.getDimensions(),
                                segmentMetrics,
                                2)
                            .subscribe(
                              evidenceResult -> {
                                try {
                                  List<String> sessionIds =
                                      evidenceResult.getSessions().stream()
                                          .map(s -> s.getSessionId())
                                          .collect(Collectors.toList());

                                  synchronized (segments) {
                                    RootCauseSegment seg = segments.get(segmentIndex);
                                    seg.setExampleSessionIds(sessionIds);
                                  }

                                  int remaining = pendingQueries.decrementAndGet();

                                  if (remaining == 0) {
                                    synchronized (segments) {
                                      JsonNode rcPayloadNode = working.get(ROOT_CAUSE_PAYLOAD_FIELD);
                                      if (rcPayloadNode instanceof ObjectNode) {
                                        ObjectNode rcPayload = (ObjectNode) rcPayloadNode;
                                        rcPayload.set("segments", objectMapper.valueToTree(segments));
                                      }
                                    }

                                    String enrichedBody = objectMapper.writeValueAsString(working);
                                    future.complete(
                                        new RcaEnrichmentOutcome(
                                            enrichedBody,
                                            rootCauseResult,
                                            date,
                                            windowEndExclusive,
                                            true));
                                  }
                                } catch (Exception e) {
                                  log.error("Error processing session evidence for segment", e);
                                  if (pendingQueries.decrementAndGet() == 0) {
                                    try {
                                      String enrichedBody = objectMapper.writeValueAsString(working);
                                      future.complete(
                                          new RcaEnrichmentOutcome(
                                              enrichedBody,
                                              rootCauseResult,
                                              date,
                                              windowEndExclusive,
                                              true));
                                    } catch (Exception e2) {
                                      future.complete(
                                          new RcaEnrichmentOutcome(
                                              fallbackBody, null, date, windowEndExclusive, false));
                                    }
                                  }
                                }
                              },
                              error -> {
                                log.error("Session evidence query failed for segment", error);
                                if (pendingQueries.decrementAndGet() == 0) {
                                  try {
                                    String enrichedBody = objectMapper.writeValueAsString(working);
                                    future.complete(
                                        new RcaEnrichmentOutcome(
                                            enrichedBody,
                                            rootCauseResult,
                                            date,
                                            windowEndExclusive,
                                            true));
                                  } catch (Exception e) {
                                    future.complete(
                                        new RcaEnrichmentOutcome(
                                            fallbackBody, null, date, windowEndExclusive, false));
                                  }
                                }
                              });
                      }
                    }
                  } else {
                    String enrichedBody = objectMapper.writeValueAsString(working);
                    future.complete(
                        new RcaEnrichmentOutcome(
                            enrichedBody, rootCauseResult, date, windowEndExclusive, true));
                  }
                } catch (Exception e) {
                  log.warn("Failed to serialize enriched RCA body: {}", e.getMessage());
                  future.complete(
                      new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
                }
              } else {
                log.warn("Root cause result has no segments");
                future.complete(
                    new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
              }
            },
            error -> {
              log.warn("Failed to fetch root-cause data for enrichment: {}", error.getMessage());
              future.complete(
                  new RcaEnrichmentOutcome(fallbackBody, null, date, windowEndExclusive, false));
            });
    return future;
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
}
