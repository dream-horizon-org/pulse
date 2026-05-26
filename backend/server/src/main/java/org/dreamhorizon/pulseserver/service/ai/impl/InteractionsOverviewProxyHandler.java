package org.dreamhorizon.pulseserver.service.ai.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.core.Completable;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.dao.rcareport.models.RcaReportCacheHit;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.Error;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;

/**
 * POST {@code interactions/overview}: read-through MySQL cache with a 1-hour TTL in
 * {@code rca_report_cache} (key: {@code (projectId, INTERACTION_OVERVIEW, "all", today-UTC)}).
 *
 * <p>Cache hit (fresh &lt; 1h): return cached body with {@code context} stripped (UI never sees it).
 * Cache miss or stale: call upstream AI, store full response (with {@code context}) in DB,
 * return stripped response to UI. Previous {@code context} is passed to the AI as
 * {@code previousContext} (RNN-analogy for conversational continuity).
 */
@Slf4j
final class InteractionsOverviewProxyHandler {

  private static final Duration CACHE_TTL = Duration.ofHours(1);
  private static final String CACHE_ENTITY_KEY = "all";
  private static final String UPSTREAM_PATH = "interactions/overview";
  // Sentinel: one logical row per project regardless of UTC day boundary.
  // Freshness is driven by cachedAt (1h TTL), not the date key.
  private static final LocalDate CACHE_DATE_SENTINEL = LocalDate.EPOCH;
  private static final String CONTEXT_FIELD = "context";
  private static final String PREVIOUS_CONTEXT_FIELD = "previousContext";
  private static final String REGENERATE_FIELD = "regenerate";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final int HTTP_INTERNAL_ERROR = 500;
  private static final String MESSAGE_PROJECT_HEADER_REQUIRED = "X-Project-ID header is required";
  private static final String ERROR_INTERNAL_BODY =
      Error.of(ServiceError.INTERNAL_SERVER_ERROR.getErrorCode(), "Internal error generating interactions overview")
          .toJsonString();

  private final AiUpstreamProxyExecutor upstream;
  private final ObjectMapper objectMapper;
  private final RcaReportCacheDao rcaReportCacheDao;

  InteractionsOverviewProxyHandler(
      AiUpstreamProxyExecutor upstream,
      ObjectMapper objectMapper,
      RcaReportCacheDao rcaReportCacheDao) {
    this.upstream = upstream;
    this.objectMapper = objectMapper;
    this.rcaReportCacheDao = rcaReportCacheDao;
  }

  CompletionStage<AiProxyUpstreamResult> handlePost(
      String rawQuery, String body, String authorization, String projectId) {
    if (projectId == null || projectId.isBlank()) {
      return CompletableFuture.completedFuture(
          badRequest(ServiceError.INCORRECT_OR_MISSING_HEADER_PARAMETERS, MESSAGE_PROJECT_HEADER_REQUIRED));
    }
    boolean regenerate = parseRegenerate(body);
    String targetUrl = upstream.buildTargetUrl(UPSTREAM_PATH, rawQuery);

    CompletableFuture<AiProxyUpstreamResult> resultFuture = new CompletableFuture<>();
    rcaReportCacheDao
        .get(projectId, RcaType.INTERACTION_OVERVIEW, CACHE_ENTITY_KEY, CACHE_DATE_SENTINEL)
        .subscribe(
            hit -> {
              boolean fresh = isFresh(hit);
              if (fresh && !regenerate) {
                String stripped = stripContext(hit.reportBody());
                String withMeta = applyCacheMetadata(stripped, true, hit.cachedAt());
                resultFuture.complete(AiProxyUpstreamResult.buffered(200, CONTENT_TYPE_JSON, withMeta));
              } else {
                // Both stale and regenerate carry forward prior context for trend continuity.
                // Cold-start (null previousContext) only happens when there is no DB row at all.
                String previousContext = extractContext(hit.reportBody());
                callUpstreamAndStore(targetUrl, authorization, projectId, CACHE_DATE_SENTINEL, previousContext)
                    .whenComplete(
                        (result, ex) -> {
                          if (ex != null) {
                            resultFuture.completeExceptionally(ex);
                          } else {
                            resultFuture.complete(result);
                          }
                        });
              }
            },
            err -> {
              log.error("Interactions overview cache lookup failed", err);
              resultFuture.complete(
                  AiProxyUpstreamResult.buffered(HTTP_INTERNAL_ERROR, CONTENT_TYPE_JSON, ERROR_INTERNAL_BODY));
            },
            () ->
                callUpstreamAndStore(targetUrl, authorization, projectId, CACHE_DATE_SENTINEL, null)
                    .whenComplete(
                        (result, ex) -> {
                          if (ex != null) {
                            resultFuture.completeExceptionally(ex);
                          } else {
                            resultFuture.complete(result);
                          }
                        }));
    return resultFuture.exceptionally(
        ex -> {
          log.error("Interactions overview proxy failed", ex);
          return AiProxyUpstreamResult.buffered(HTTP_INTERNAL_ERROR, CONTENT_TYPE_JSON, ERROR_INTERNAL_BODY);
        });
  }

  private CompletionStage<AiProxyUpstreamResult> callUpstreamAndStore(
      String targetUrl,
      String authorization,
      String projectId,
      LocalDate cacheDate,
      String previousContext) {
    String upstreamBody = buildUpstreamBody(previousContext);
    return upstream
        .executeProxy("POST", targetUrl, upstreamBody, authorization, projectId)
        .thenCompose(result -> persistAndStripContext(result, projectId, CACHE_DATE_SENTINEL));
  }

  private CompletionStage<AiProxyUpstreamResult> persistAndStripContext(
      AiProxyUpstreamResult result, String projectId, LocalDate cacheDate) {
    if (!AiProxyUpstreamResult.isSuccessfulBuffered(result)) {
      return CompletableFuture.completedFuture(result);
    }
    String fullBody = result.getBufferedBody();
    Completable putOp =
        rcaReportCacheDao.put(
            projectId, RcaType.INTERACTION_OVERVIEW, CACHE_ENTITY_KEY, cacheDate, fullBody);
    String stripped = stripContext(fullBody);
    String withMeta = applyCacheMetadata(stripped, false, Instant.now());
    AiProxyUpstreamResult toReturn =
        AiProxyUpstreamResult.buffered(result.getStatusCode(), result.getMediaType(), withMeta);
    // Fire-and-forget cache write — do not gate the response on it.
    // A write failure is logged but should not surface as a user-facing error.
    putOp.subscribe(
        () -> {},
        err -> log.warn("Failed to persist interactions overview to cache, projectId={}", projectId, err));
    return CompletableFuture.completedFuture(toReturn);
  }

  private String buildUpstreamBody(String previousContext) {
    try {
      ObjectNode node = objectMapper.createObjectNode();
      if (previousContext != null) {
        node.put(PREVIOUS_CONTEXT_FIELD, previousContext);
      }
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  private String stripContext(String fullJson) {
    try {
      JsonNode node = objectMapper.readTree(fullJson);
      if (node instanceof ObjectNode obj) {
        obj.remove(CONTEXT_FIELD);
        return objectMapper.writeValueAsString(obj);
      }
      log.warn("stripContext: upstream response is not a JSON object — context not stripped");
      return fullJson;
    } catch (Exception e) {
      log.warn("stripContext: failed to parse upstream response", e);
      return fullJson;
    }
  }

  private String extractContext(String fullJson) {
    try {
      JsonNode node = objectMapper.readTree(fullJson);
      JsonNode contextNode = node.path(CONTEXT_FIELD);
      if (contextNode.isMissingNode() || contextNode.isNull()) {
        return null;
      }
      String text = contextNode.asText(null);
      return (text == null || text.isBlank()) ? null : text;
    } catch (Exception e) {
      return null;
    }
  }

  private String applyCacheMetadata(String body, boolean cached, Instant cachedAt) {
    try {
      JsonNode node = objectMapper.readTree(body);
      if (node instanceof ObjectNode obj) {
        obj.put("cached", cached);
        if (cachedAt != null) {
          obj.put("cachedAt", DateTimeFormatter.ISO_INSTANT.format(cachedAt));
        }
        return objectMapper.writeValueAsString(obj);
      }
      log.warn("applyCacheMetadata: response body is not a JSON object — metadata not injected");
      return body;
    } catch (Exception e) {
      log.warn("applyCacheMetadata: failed to parse response body", e);
      return body;
    }
  }

  private boolean isFresh(RcaReportCacheHit hit) {
    Instant cachedAt = hit.cachedAt();
    if (cachedAt == null) {
      return false;
    }
    return cachedAt.isAfter(Instant.now().minus(CACHE_TTL));
  }

  private boolean parseRegenerate(String body) {
    if (body == null || body.isBlank()) {
      return false;
    }
    try {
      JsonNode node = objectMapper.readTree(body);
      JsonNode regenerateNode = node.get(REGENERATE_FIELD);
      if (regenerateNode == null || regenerateNode.isNull()) {
        return false;
      }
      return regenerateNode.isBoolean() && regenerateNode.booleanValue();
    } catch (Exception e) {
      return false;
    }
  }

  private static AiProxyUpstreamResult badRequest(ServiceError error, String message) {
    int statusCode = error.getHttpStatusCode();
    String json = Error.of(error.getErrorCode(), message).toJsonString();
    return AiProxyUpstreamResult.buffered(statusCode, CONTENT_TYPE_JSON, json);
  }
}
