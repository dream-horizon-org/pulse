package org.dreamhorizon.pulseserver.service.ai.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.Error;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;

/**
 * POST {@code rca/session-report}: read-through MySQL cache in {@code rca_report_cache} with
 * {@link RcaType#SESSION}. Project-wide — no entity name; cache key uses sentinel
 * {@value SESSION_ENTITY_KEY}. Optional {@code regenerate} bypasses cache.
 */
@Slf4j
final class SessionRcaNarrativeProxyHandler {

  static final String SESSION_ENTITY_KEY = "__session__";
  private static final String RCA_SESSION_REPORT_PATH = "rca/session-report";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String DATE_FIELD = "date";
  private static final String AS_OF_FIELD = "asOf";
  private static final String ROOT_CAUSE_PAYLOAD_FIELD = "rootCausePayload";
  private static final String REGENERATE_FIELD = "regenerate";
  private static final int HTTP_INTERNAL_ERROR = 500;
  private static final String ERROR_INTERNAL_BODY =
      Error.of(ServiceError.INTERNAL_SERVER_ERROR.getErrorCode(),
          "Internal error generating session RCA narrative").toJsonString();
  private static final int HTTP_DATABASE_ERROR = ServiceError.DATABASE_ERROR.getHttpStatusCode();
  private static final String ERROR_CACHE_READ_BODY =
      Error.of(ServiceError.DATABASE_ERROR.getErrorCode(),
          ServiceError.DATABASE_ERROR.getErrorMessage()).toJsonString();

  private static final String MSG_BODY_REQUIRED = "Request body is required";
  private static final String MSG_PROJECT_REQUIRED = "X-Project-ID header is required";
  private static final String MSG_BODY_JSON_OBJECT = "Request body must be a JSON object";
  private static final String MSG_PAYLOAD_REQUIRED = "rootCausePayload is required";

  private final AiUpstreamProxyExecutor upstream;
  private final ObjectMapper objectMapper;
  private final RcaReportCacheDao cacheDao;
  private final RootCauseConfig rootCauseConfig;

  SessionRcaNarrativeProxyHandler(
      AiUpstreamProxyExecutor upstream,
      ObjectMapper objectMapper,
      RcaReportCacheDao cacheDao,
      RootCauseConfig rootCauseConfig) {
    this.upstream = upstream;
    this.objectMapper = objectMapper;
    this.cacheDao = cacheDao;
    this.rootCauseConfig = rootCauseConfig;
  }

  CompletionStage<AiProxyUpstreamResult> handlePost(
      String rawQuery, String body, String authorization, String projectId) {
    Validation validation = validate(body, projectId);
    if (validation instanceof Validation.Invalid invalid) {
      return CompletableFuture.completedFuture(invalid.response());
    }
    Parsed parsed = ((Validation.Valid) validation).parsed();
    CacheKey key = parsed.key();
    String targetUrl = upstream.buildTargetUrl(RCA_SESSION_REPORT_PATH, rawQuery);
    if (key.regenerate()) {
      return withErrorLogging(doProxy(targetUrl, parsed, authorization));
    }
    return withErrorLogging(proxyAfterCacheLookup(key, targetUrl, parsed, authorization));
  }

  private CompletionStage<AiProxyUpstreamResult> withErrorLogging(
      CompletionStage<AiProxyUpstreamResult> stage) {
    return stage.exceptionally(ex -> {
      log.error("Session RCA narrative proxy failed", ex);
      return AiProxyUpstreamResult.buffered(HTTP_INTERNAL_ERROR, CONTENT_TYPE_JSON, ERROR_INTERNAL_BODY);
    });
  }

  private CompletionStage<AiProxyUpstreamResult> proxyAfterCacheLookup(
      CacheKey key, String targetUrl, Parsed parsed, String authorization) {
    CompletableFuture<AiProxyUpstreamResult> future = new CompletableFuture<>();
    cacheDao.get(key.projectId(), RcaType.SESSION, SESSION_ENTITY_KEY, key.anchorDate())
        .subscribe(
            hit -> future.complete(
                AiProxyUpstreamResult.buffered(200, CONTENT_TYPE_JSON,
                    applyCacheMetadata(hit.reportBody(), true, hit.cachedAt()))),
            err -> {
              log.error("Session RCA narrative MySQL cache lookup failed", err);
              future.complete(AiProxyUpstreamResult.buffered(
                  HTTP_DATABASE_ERROR, CONTENT_TYPE_JSON, ERROR_CACHE_READ_BODY));
            },
            () -> doProxy(targetUrl, parsed, authorization)
                .whenComplete((result, ex) -> {
                  if (ex != null) {
                    future.completeExceptionally(ex);
                  } else {
                    future.complete(result);
                  }
                }));
    return future;
  }

  private CompletionStage<AiProxyUpstreamResult> doProxy(
      String targetUrl, Parsed parsed, String authorization) {
    ObjectNode working = parsed.bodyRoot().deepCopy();
    working.remove(REGENERATE_FIELD);
    String bodyToSend;
    try {
      bodyToSend = objectMapper.writeValueAsString(working);
    } catch (JsonProcessingException e) {
      return CompletableFuture.completedFuture(
          AiProxyUpstreamResult.buffered(HTTP_INTERNAL_ERROR, CONTENT_TYPE_JSON, ERROR_INTERNAL_BODY));
    }
    return upstream
        .executeProxy("POST", targetUrl, bodyToSend, authorization, parsed.key().projectId())
        .thenCompose(result -> persistReport(result, parsed.key()));
  }

  private CompletionStage<AiProxyUpstreamResult> persistReport(
      AiProxyUpstreamResult result, CacheKey key) {
    if (!AiProxyUpstreamResult.isSuccessfulBuffered(result)) {
      return CompletableFuture.completedFuture(result);
    }
    String withMeta = applyCacheMetadata(result.getBufferedBody(), true, Instant.now());
    AiProxyUpstreamResult updated =
        AiProxyUpstreamResult.buffered(result.getStatusCode(), result.getMediaType(), withMeta);
    Completable putOp = cacheDao.put(
        key.projectId(), RcaType.SESSION, SESSION_ENTITY_KEY, key.anchorDate(),
        updated.getBufferedBody());
    CompletableFuture<AiProxyUpstreamResult> done = new CompletableFuture<>();
    putOp.andThen(io.reactivex.rxjava3.core.Single.just(updated))
        .subscribe(done::complete, done::completeExceptionally);
    return done;
  }

  private Validation validate(String body, String projectId) {
    if (body == null || body.isBlank()) {
      return invalid(ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MSG_BODY_REQUIRED);
    }
    if (projectId == null || projectId.isBlank()) {
      return invalid(ServiceError.INCORRECT_OR_MISSING_HEADER_PARAMETERS, MSG_PROJECT_REQUIRED);
    }
    try {
      JsonNode tree = objectMapper.readTree(body);
      if (!(tree instanceof ObjectNode root)) {
        return invalid(ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MSG_BODY_JSON_OBJECT);
      }
      JsonNode payloadNode = root.get(ROOT_CAUSE_PAYLOAD_FIELD);
      if (payloadNode == null || payloadNode.isNull() || !payloadNode.isObject()) {
        return invalid(ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MSG_PAYLOAD_REQUIRED);
      }
      LocalDate anchorDate = resolveAnchorDate(root.get(DATE_FIELD), root.get(AS_OF_FIELD));
      boolean regenerate = isRegenerateRequested(root.get(REGENERATE_FIELD));
      return new Validation.Valid(new Parsed(root, new CacheKey(projectId.trim(), anchorDate, regenerate)));
    } catch (Exception e) {
      log.debug("Invalid session RCA narrative JSON: {}", e.getMessage());
      return invalid(ServiceError.INVALID_REQUEST_BODY, ServiceError.INVALID_REQUEST_BODY.getErrorMessage());
    }
  }

  private LocalDate resolveAnchorDate(JsonNode dateNode, JsonNode asOfNode) {
    if (dateNode != null && !dateNode.isNull()) {
      String text = dateNode.asText().trim();
      if (!text.isBlank()) {
        try {
          return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
          // fall through to asOf derivation
        }
      }
    }
    if (asOfNode != null && !asOfNode.isNull()) {
      String asOfText = asOfNode.asText().trim();
      if (!asOfText.isBlank()) {
        try {
          return Instant.parse(asOfText).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeParseException ignored) {
          // fall through to today
        }
      }
    }
    return LocalDate.now(java.time.ZoneOffset.UTC);
  }

  private static boolean isRegenerateRequested(JsonNode node) {
    return node != null && !node.isNull() && node.isBoolean() && node.booleanValue();
  }

  private static Validation invalid(ServiceError error, String message) {
    String json = Error.of(error.getErrorCode(), message).toJsonString();
    return new Validation.Invalid(
        AiProxyUpstreamResult.buffered(error.getHttpStatusCode(), CONTENT_TYPE_JSON, json));
  }

  private String applyCacheMetadata(String body, boolean cached, Instant cachedAt) {
    try {
      JsonNode node = objectMapper.readTree(body);
      if (node instanceof ObjectNode obj) {
        obj.put("cached", cached);
        if (cachedAt != null) {
          obj.put("cachedAt", DateTimeFormatter.ISO_INSTANT.format(cachedAt));
        }
      }
      return objectMapper.writeValueAsString(node);
    } catch (Exception e) {
      return body;
    }
  }

  private record CacheKey(String projectId, LocalDate anchorDate, boolean regenerate) {}
  private record Parsed(ObjectNode bodyRoot, CacheKey key) {}

  private sealed interface Validation permits Validation.Valid, Validation.Invalid {
    record Valid(Parsed parsed) implements Validation {}
    record Invalid(AiProxyUpstreamResult response) implements Validation {}
  }
}
