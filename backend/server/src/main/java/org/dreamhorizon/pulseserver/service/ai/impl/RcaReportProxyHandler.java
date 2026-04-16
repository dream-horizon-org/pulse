package org.dreamhorizon.pulseserver.service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.Error;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.rootcause.RcaRelatedHeatmapsMerger;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.SessionEvidenceService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;

/**
 * POST {@code rca/report}: read-through MySQL cache, optional {@code regenerate}, body enrichment
 * ({@code rootCausePayload} via {@link RootCauseService}), then upstream AI via {@link
 * AiUpstreamProxyExecutor}.
 */
@Slf4j
final class RcaReportProxyHandler {

  private static final String RCA_REPORT_PATH = "rca/report";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String INTERACTION_NAME_FIELD = "interactionName";
  private static final String DATE_FIELD = "date";
  private static final String REGENERATE_FIELD = "regenerate";
  private static final int HTTP_INTERNAL_ERROR = 500;
  private static final String ERROR_MESSAGE_RCA_INTERNAL = "Internal error generating RCA report";
  private static final String ERROR_INTERNAL_RCA_BODY =
      Error.of(ServiceError.INTERNAL_SERVER_ERROR.getErrorCode(), ERROR_MESSAGE_RCA_INTERNAL)
          .toJsonString();
  private static final int HTTP_DATABASE_ERROR = ServiceError.DATABASE_ERROR.getHttpStatusCode();
  private static final String ERROR_RCA_CACHE_READ_BODY =
      Error.of(
              ServiceError.DATABASE_ERROR.getErrorCode(),
              ServiceError.DATABASE_ERROR.getErrorMessage())
          .toJsonString();
  private static final String ROOT_CAUSE_PAYLOAD_FIELD = "rootCausePayload";
  private static final String MESSAGE_BODY_REQUIRED = "Request body is required";
  private static final String MESSAGE_PROJECT_HEADER_REQUIRED = "X-Project-ID header is required";
  private static final String MESSAGE_BODY_JSON_OBJECT = "RCA report body must be a JSON object";
  private static final String MESSAGE_INTERACTION_REQUIRED = "interactionName is required";

  private final AiUpstreamProxyExecutor upstream;
  private final ObjectMapper objectMapper;
  private final RootCauseService rootCauseService;
  private final RcaReportCacheDao rcaReportCacheDao;
  private final RootCauseConfig rootCauseConfig;
  private final RcaRelatedHeatmapsMerger rcaRelatedHeatmapsMerger;
  private final SessionEvidenceService sessionEvidenceService;

  RcaReportProxyHandler(
      AiUpstreamProxyExecutor upstream,
      ObjectMapper objectMapper,
      RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao,
      RootCauseConfig rootCauseConfig,
      RcaRelatedHeatmapsMerger rcaRelatedHeatmapsMerger,
      SessionEvidenceService sessionEvidenceService) {
    this.upstream = upstream;
    this.objectMapper = objectMapper;
    this.rootCauseService = rootCauseService;
    this.rcaReportCacheDao = rcaReportCacheDao;
    this.rootCauseConfig = rootCauseConfig;
    this.rcaRelatedHeatmapsMerger = rcaRelatedHeatmapsMerger;
    this.sessionEvidenceService = sessionEvidenceService;
  }

  CompletionStage<AiProxyUpstreamResult> handlePost(
      String rawQuery, String body, String authorization, String projectId) {
    RcaPostValidation validation = validateRcaReportPost(body, projectId);
    if (validation instanceof RcaPostValidation.Invalid invalid) {
      return CompletableFuture.completedFuture(invalid.response());
    }
    ParsedRcaPost parsed = ((RcaPostValidation.Valid) validation).parsed();
    RcaCacheKeyParts keyParts = parsed.keyParts();
    String targetUrl = upstream.buildTargetUrl(RCA_REPORT_PATH, rawQuery);
    if (keyParts.regenerate()) {
      return withRcaErrorLogging(doEnrichAndProxyRca(targetUrl, parsed, authorization, keyParts, true));
    }
    return withRcaErrorLogging(
        proxyRcaAfterMysqlCacheLookup(keyParts, targetUrl, parsed, authorization));
  }

  private CompletionStage<AiProxyUpstreamResult> withRcaErrorLogging(
      CompletionStage<AiProxyUpstreamResult> stage) {
    return stage.exceptionally(
        ex -> {
          log.error("RCA report proxy failed", ex);
          return AiProxyUpstreamResult.buffered(
              HTTP_INTERNAL_ERROR, CONTENT_TYPE_JSON, ERROR_INTERNAL_RCA_BODY);
        });
  }

  private CompletionStage<AiProxyUpstreamResult> doEnrichAndProxyRca(
      String targetUrl,
      ParsedRcaPost parsed,
      String authorization,
      RcaCacheKeyParts keyParts,
      boolean forceRootCauseRefresh) {
    String projectId = keyParts.projectId();
    return enrichRcaBodyAsync(parsed, forceRootCauseRefresh)
        .thenCompose(
            outcome ->
                upstream
                    .executeProxy("POST", targetUrl, outcome.body(), authorization, projectId)
                    .thenCompose(proxyResult -> finalizeSuccessfulRcaProxyResult(proxyResult, outcome, keyParts)));
  }

  /**
   * Read-through MySQL cache: on hit returns cached JSON; on miss enriches and proxies upstream; on
   * DAO error completes with {@link ServiceError#DATABASE_ERROR} (does not call AI).
   */
  private CompletionStage<AiProxyUpstreamResult> proxyRcaAfterMysqlCacheLookup(
      RcaCacheKeyParts keyParts,
      String targetUrl,
      ParsedRcaPost parsed,
      String authorization) {
    CompletableFuture<AiProxyUpstreamResult> resultFuture = new CompletableFuture<>();
    rcaReportCacheDao
        .get(keyParts.projectId(), keyParts.interactionName(), keyParts.date())
        .subscribe(
            hit ->
                resultFuture.complete(
                    AiProxyUpstreamResult.buffered(
                        200,
                        CONTENT_TYPE_JSON,
                        applyCacheMetadata(hit.reportBody(), true, hit.cachedAt()))),
            err -> {
              log.error("RCA MySQL cache lookup failed", err);
              resultFuture.complete(rcaCacheReadFailedResult());
            },
            () ->
                doEnrichAndProxyRca(targetUrl, parsed, authorization, keyParts, false)
                    .whenComplete(
                        (result, ex) -> {
                          if (ex != null) {
                            resultFuture.completeExceptionally(ex);
                          } else {
                            resultFuture.complete(result);
                          }
                        }));
    return resultFuture;
  }

  private static AiProxyUpstreamResult rcaCacheReadFailedResult() {
    return AiProxyUpstreamResult.buffered(
        HTTP_DATABASE_ERROR, CONTENT_TYPE_JSON, ERROR_RCA_CACHE_READ_BODY);
  }

  private record RcaCacheKeyParts(
      String projectId, String interactionName, LocalDate date, boolean regenerate) {}

  /** Parsed JSON body and cache key after {@link #validateRcaReportPost} succeeds. */
  private record ParsedRcaPost(String rawBody, ObjectNode bodyRoot, RcaCacheKeyParts keyParts) {}

  /**
   * Outcome of root-cause enrichment: request body sent upstream plus context for merging related
   * heatmaps after the AI response.
   */
  private record RcaEnrichmentOutcome(
      String body,
      RootCauseResult rootCause,
      LocalDate anchorDate,
      Instant windowEndExclusive,
      boolean enrichmentOk) {}

  private sealed interface RcaPostValidation permits RcaPostValidation.Valid, RcaPostValidation.Invalid {
    record Valid(ParsedRcaPost parsed) implements RcaPostValidation {}

    record Invalid(AiProxyUpstreamResult response) implements RcaPostValidation {}
  }

  private RcaPostValidation validateRcaReportPost(String body, String projectId) {
    boolean bodyMissing = body == null || body.isBlank();
    if (bodyMissing) {
      AiProxyUpstreamResult errorResponse =
          badRequest(ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MESSAGE_BODY_REQUIRED);
      return new RcaPostValidation.Invalid(errorResponse);
    }
    boolean projectMissing = projectId == null || projectId.isBlank();
    if (projectMissing) {
      AiProxyUpstreamResult errorResponse =
          badRequest(
              ServiceError.INCORRECT_OR_MISSING_HEADER_PARAMETERS, MESSAGE_PROJECT_HEADER_REQUIRED);
      return new RcaPostValidation.Invalid(errorResponse);
    }
    try {
      JsonNode tree = objectMapper.readTree(body);
      if (!(tree instanceof ObjectNode objectRoot)) {
        AiProxyUpstreamResult errorResponse =
            badRequest(ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MESSAGE_BODY_JSON_OBJECT);
        return new RcaPostValidation.Invalid(errorResponse);
      }
      JsonNode interactionNode = objectRoot.get(INTERACTION_NAME_FIELD);
      boolean interactionMissing = interactionNode == null || interactionNode.asText().isBlank();
      if (interactionMissing) {
        AiProxyUpstreamResult errorResponse =
            badRequest(
                ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MESSAGE_INTERACTION_REQUIRED);
        return new RcaPostValidation.Invalid(errorResponse);
      }
      LocalDate date = resolveDateFromNode(objectRoot.get(DATE_FIELD));
      boolean regenerate = isRegenerateRequested(objectRoot.get(REGENERATE_FIELD));
      String interactionName = interactionNode.asText();
      RcaCacheKeyParts keyParts = new RcaCacheKeyParts(projectId, interactionName, date, regenerate);
      ParsedRcaPost parsed = new ParsedRcaPost(body, objectRoot, keyParts);
      return new RcaPostValidation.Valid(parsed);
    } catch (Exception e) {
      log.debug("Invalid RCA report JSON: {}", e.getMessage());
      AiProxyUpstreamResult errorResponse =
          badRequest(
              ServiceError.INVALID_REQUEST_BODY, ServiceError.INVALID_REQUEST_BODY.getErrorMessage());
      return new RcaPostValidation.Invalid(errorResponse);
    }
  }

  private static AiProxyUpstreamResult badRequest(ServiceError error, String message) {
    int statusCode = error.getHttpStatusCode();
    String json = Error.of(error.getErrorCode(), message).toJsonString();
    return AiProxyUpstreamResult.buffered(statusCode, CONTENT_TYPE_JSON, json);
  }

  private static boolean isRegenerateRequested(JsonNode regenerateNode) {
    if (regenerateNode == null || regenerateNode.isNull()) {
      return false;
    }
    return regenerateNode.isBoolean() && regenerateNode.booleanValue();
  }

  /**
   * On successful buffered RCA JSON, merges per-segment related heatmaps when enrichment
   * succeeded, sets {@code cached} and {@code cachedAt} (for UI), persists to MySQL, returns
   * updated result.
   */
  private CompletionStage<AiProxyUpstreamResult> finalizeSuccessfulRcaProxyResult(
      AiProxyUpstreamResult result, RcaEnrichmentOutcome enrichment, RcaCacheKeyParts keyParts) {
    if (!AiProxyUpstreamResult.isSuccessfulBuffered(result)) {
      return CompletableFuture.completedFuture(result);
    }
    String body = result.getBufferedBody();
    if (enrichment.enrichmentOk()
        && enrichment.rootCause() != null
        && enrichment.rootCause().getSegments() != null
        && !enrichment.rootCause().getSegments().isEmpty()) {
      try {
        JsonNode tree = objectMapper.readTree(body);
        if (tree instanceof ObjectNode root) {
          RootCauseQueryBuilder.Window window =
              new RootCauseQueryBuilder.Window(
                  enrichment.anchorDate(),
                  rootCauseConfig.getLookbackDays(),
                  enrichment.windowEndExclusive());
          CompletableFuture<AiProxyUpstreamResult> done = new CompletableFuture<>();
          rootCauseService
              .fetchDistinctScreensForInteraction(
                  keyParts.projectId(), keyParts.interactionName(), window)
              .subscribeOn(Schedulers.io())
              .subscribe(
                  screens -> {
                    try {
                      rcaRelatedHeatmapsMerger.mergeInto(
                          root, enrichment.rootCause().getSegments(), window, screens);
                      String merged = objectMapper.writeValueAsString(root);
                      persistBufferedRcaReport(merged, result, keyParts)
                          .whenComplete(
                              (updated, err) -> {
                                if (err != null) {
                                  done.completeExceptionally(err);
                                } else {
                                  done.complete(updated);
                                }
                              });
                    } catch (Exception e) {
                      log.warn("Failed to merge RCA related heatmaps: {}", e.getMessage());
                      persistBufferedRcaReport(body, result, keyParts)
                          .whenComplete(
                              (updated, err) -> {
                                if (err != null) {
                                  done.completeExceptionally(err);
                                } else {
                                  done.complete(updated);
                                }
                              });
                    }
                  },
                  err -> done.completeExceptionally(err));
          return done;
        }
      } catch (Exception e) {
        log.warn("Failed to merge RCA related heatmaps: {}", e.getMessage());
      }
    }
    return persistBufferedRcaReport(body, result, keyParts);
  }

  private CompletionStage<AiProxyUpstreamResult> persistBufferedRcaReport(
      String body, AiProxyUpstreamResult result, RcaCacheKeyParts keyParts) {
    String withMeta = applyCacheMetadata(body, true, Instant.now());
    String mediaType = result.getMediaType();
    AiProxyUpstreamResult updated =
        AiProxyUpstreamResult.buffered(result.getStatusCode(), mediaType, withMeta);
    Completable putOp =
        rcaReportCacheDao.put(
            keyParts.projectId(),
            keyParts.interactionName(),
            keyParts.date(),
            updated.getBufferedBody());
    CompletableFuture<AiProxyUpstreamResult> done = new CompletableFuture<>();
    putOp.andThen(Single.just(updated)).subscribe(done::complete, done::completeExceptionally);
    return done;
  }

  private CompletionStage<RcaEnrichmentOutcome> enrichRcaBodyAsync(
      ParsedRcaPost parsed, boolean forceRootCauseRefresh) {
    ObjectNode working = parsed.bodyRoot().deepCopy();
    working.remove(REGENERATE_FIELD);
    String fallbackBody = parsed.rawBody();
    RcaCacheKeyParts keyParts = parsed.keyParts();
    String projectId = keyParts.projectId();
    String interactionName = keyParts.interactionName();
    LocalDate date = keyParts.date();
    Instant windowEndExclusive = Instant.now();

    CompletableFuture<RcaEnrichmentOutcome> future = new CompletableFuture<>();
    rootCauseService
        .getRootCause(projectId, interactionName, date, windowEndExclusive, forceRootCauseRefresh)
        .subscribe(
            rootCauseResult -> {
              if (rootCauseResult.getSegments() != null) {
                try {
                  JsonNode resultNode = objectMapper.valueToTree(rootCauseResult);
                  working.set(ROOT_CAUSE_PAYLOAD_FIELD, resultNode);

                  // Get session evidence for each segment
                  if (!rootCauseResult.getSegments().isEmpty()) {
                    List<RootCauseSegment> segments = rootCauseResult.getSegments();
                    LocalDate lookbackStart = date.minusDays(6);  // 7 days including today

                    java.util.concurrent.atomic.AtomicInteger pendingQueries = 
                        new java.util.concurrent.atomic.AtomicInteger(segments.size());

                    for (int i = 0; i < segments.size(); i++) {
                      RootCauseSegment segment = segments.get(i);
                      final int segmentIndex = i;
                      Map<String, Double> segmentMetrics = extractSegmentMetrics(segment.getMetrics());

                      sessionEvidenceService
                        .getSessionEvidence(
                            projectId,
                            interactionName,
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

                                // Attach sessions directly to this segment
                                synchronized (segments) {
                                  RootCauseSegment seg = segments.get(segmentIndex);
                                  seg.setExampleSessionIds(sessionIds);
                                }

                                int remaining = pendingQueries.decrementAndGet();
                                
                                if (remaining == 0) {
                                  // All queries complete - update payload with enriched segments
                                  JsonNode rcPayloadNode = working.get(ROOT_CAUSE_PAYLOAD_FIELD);
                                  if (rcPayloadNode instanceof ObjectNode) {
                                    ObjectNode rcPayload = (ObjectNode) rcPayloadNode;
                                    rcPayload.set("segments", objectMapper.valueToTree(segments));
                                  }

                                  String enrichedBody = objectMapper.writeValueAsString(working);
                                  future.complete(
                                      new RcaEnrichmentOutcome(
                                          enrichedBody, rootCauseResult, date, windowEndExclusive, true));
                                }
                              } catch (Exception e) {
                                log.error("Error processing session evidence for segment", e);
                                if (pendingQueries.decrementAndGet() == 0) {
                                  try {
                                    String enrichedBody = objectMapper.writeValueAsString(working);
                                    future.complete(
                                        new RcaEnrichmentOutcome(
                                            enrichedBody, rootCauseResult, date, windowEndExclusive, true));
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
                                          enrichedBody, rootCauseResult, date, windowEndExclusive, true));
                                } catch (Exception e) {
                                  future.complete(
                                      new RcaEnrichmentOutcome(
                                          fallbackBody, null, date, windowEndExclusive, false));
                                }
                              }
                            });
                    }
                  } else {
                    // No segments - return payload as-is
                    String enrichedBody = objectMapper.writeValueAsString(working);
                    future.complete(
                        new RcaEnrichmentOutcome(
                            enrichedBody, rootCauseResult, date, windowEndExclusive, true));
                  }
                } catch (Exception e) {
                  log.warn("Failed to serialize enriched RCA body: {}", e.getMessage());
                  future.complete(
                      new RcaEnrichmentOutcome(
                          fallbackBody, null, date, windowEndExclusive, false));
                }
              } else {
                log.warn("Root cause result has no segments");
                future.complete(
                    new RcaEnrichmentOutcome(
                        fallbackBody, null, date, windowEndExclusive, false));
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
   * Extract error_rate and apdex metrics from segment metrics map.
   * Converts Object values to Double for numeric comparison in query.
   */
  private Map<String, Double> extractSegmentMetrics(Map<String, Object> metrics) {
    Map<String, Double> result = new java.util.HashMap<>();
    if (metrics != null) {
      Object errorRateObj = metrics.get("error_rate");
      if (errorRateObj != null) {
        try {
          result.put("error_rate", Double.parseDouble(errorRateObj.toString()));
        } catch (NumberFormatException e) {
          log.warn("Failed to parse error_rate metric: {}", errorRateObj);
        }
      }
      Object apdexObj = metrics.get("apdex");
      if (apdexObj != null) {
        try {
          result.put("apdex", Double.parseDouble(apdexObj.toString()));
        } catch (NumberFormatException e) {
          log.warn("Failed to parse apdex metric: {}", apdexObj);
        }
      }
    }
    return result;
  }

  private LocalDate resolveDateFromNode(JsonNode dateNode) {
    if (dateNode == null || dateNode.isNull()) {
      return LocalDate.now(ZoneOffset.UTC);
    }
    String dateValue = dateNode.asText();
    boolean isDateMissing = dateValue == null || dateValue.isBlank();
    if (isDateMissing) {
      return LocalDate.now(ZoneOffset.UTC);
    }
    try {
      return LocalDate.parse(dateValue);
    } catch (DateTimeParseException e) {
      return LocalDate.now(ZoneOffset.UTC);
    }
  }

  private String applyCacheMetadata(String body, boolean cached, Instant cachedAt) {
    try {
      JsonNode node = objectMapper.readTree(body);
      if (node instanceof ObjectNode) {
        ObjectNode obj = (ObjectNode) node;
        obj.put("cached", cached);
        if (cachedAt != null) {
          obj.put("cachedAt", DateTimeFormatter.ISO_INSTANT.format(cachedAt));
        }
      }
      return objectMapper.writeValueAsString(node);
    } catch (Exception exception) {
      return body;
    }
  }
}
