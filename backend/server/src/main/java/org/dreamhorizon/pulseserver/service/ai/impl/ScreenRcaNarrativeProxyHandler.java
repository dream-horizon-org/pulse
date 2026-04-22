package org.dreamhorizon.pulseserver.service.ai.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.core.Completable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.rcareport.ScreenRcaNarrativeCacheDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.Error;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;

/**
 * POST {@code rca/screen-report}: read-through MySQL cache keyed by project, screen, the UTC
 * calendar dates of the request {@code start}/{@code end} instants (not sub-day time), and a
 * fingerprint of {@code rootCausePayload}; optional {@code regenerate} bypasses cache; forwards
 * full instants to pulse_ai when uncached.
 */
@Slf4j
final class ScreenRcaNarrativeProxyHandler {

  private static final String RCA_SCREEN_REPORT_PATH = "rca/screen-report";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String SCREEN_NAME_FIELD = "screenName";
  private static final String START_FIELD = "start";
  private static final String END_FIELD = "end";
  private static final String ROOT_CAUSE_PAYLOAD_FIELD = "rootCausePayload";
  private static final String REGENERATE_FIELD = "regenerate";
  private static final int HTTP_INTERNAL_ERROR = 500;
  private static final String ERROR_MESSAGE_INTERNAL = "Internal error generating screen RCA narrative";
  private static final String ERROR_INTERNAL_BODY =
      Error.of(ServiceError.INTERNAL_SERVER_ERROR.getErrorCode(), ERROR_MESSAGE_INTERNAL)
          .toJsonString();
  private static final int HTTP_DATABASE_ERROR = ServiceError.DATABASE_ERROR.getHttpStatusCode();
  private static final String ERROR_SCREEN_RCA_CACHE_READ_BODY =
      Error.of(
              ServiceError.DATABASE_ERROR.getErrorCode(),
              ServiceError.DATABASE_ERROR.getErrorMessage())
          .toJsonString();

  private static final String MESSAGE_BODY_REQUIRED = "Request body is required";
  private static final String MESSAGE_PROJECT_HEADER_REQUIRED = "X-Project-ID header is required";
  private static final String MESSAGE_BODY_JSON_OBJECT = "Request body must be a JSON object";
  private static final String MESSAGE_SCREEN_NAME_REQUIRED = "screenName is required";
  private static final String MESSAGE_WINDOW_REQUIRED =
      "Body fields start and end (ISO-8601 instants) are required for screen RCA narrative";
  private static final String MESSAGE_ROOT_CAUSE_PAYLOAD_REQUIRED =
      "rootCausePayload is required for screen RCA narrative";

  private final AiUpstreamProxyExecutor upstream;
  private final ObjectMapper objectMapper;
  private final ScreenRcaNarrativeCacheDao screenRcaNarrativeCacheDao;

  ScreenRcaNarrativeProxyHandler(
      AiUpstreamProxyExecutor upstream,
      ObjectMapper objectMapper,
      ScreenRcaNarrativeCacheDao screenRcaNarrativeCacheDao) {
    this.upstream = upstream;
    this.objectMapper = objectMapper;
    this.screenRcaNarrativeCacheDao = screenRcaNarrativeCacheDao;
  }

  CompletionStage<AiProxyUpstreamResult> handlePost(
      String rawQuery, String body, String authorization, String projectId) {
    ScreenPostValidation validation = validateScreenRcaNarrativePost(body, projectId);
    if (validation instanceof ScreenPostValidation.Invalid invalid) {
      return CompletableFuture.completedFuture(invalid.response());
    }
    ParsedScreenRcaPost parsed = ((ScreenPostValidation.Valid) validation).parsed();
    ScreenNarrativeCacheKeyParts keyParts = parsed.keyParts();
    String targetUrl = upstream.buildTargetUrl(RCA_SCREEN_REPORT_PATH, rawQuery);
    if (keyParts.regenerate()) {
      return withScreenRcaErrorLogging(doProxyScreenNarrative(targetUrl, parsed, authorization, keyParts));
    }
    return withScreenRcaErrorLogging(
        proxyScreenNarrativeAfterMysqlCacheLookup(keyParts, targetUrl, parsed, authorization));
  }

  private CompletionStage<AiProxyUpstreamResult> withScreenRcaErrorLogging(
      CompletionStage<AiProxyUpstreamResult> stage) {
    return stage.exceptionally(
        ex -> {
          log.error("Screen RCA narrative proxy failed", ex);
          return AiProxyUpstreamResult.buffered(
              HTTP_INTERNAL_ERROR, CONTENT_TYPE_JSON, ERROR_INTERNAL_BODY);
        });
  }

  private CompletionStage<AiProxyUpstreamResult> proxyScreenNarrativeAfterMysqlCacheLookup(
      ScreenNarrativeCacheKeyParts keyParts,
      String targetUrl,
      ParsedScreenRcaPost parsed,
      String authorization) {
    CompletableFuture<AiProxyUpstreamResult> resultFuture = new CompletableFuture<>();
    screenRcaNarrativeCacheDao
        .get(
            keyParts.projectId(),
            keyParts.screenName(),
            keyParts.windowStartDateUtc(),
            keyParts.windowEndDateUtc(),
            keyParts.payloadFingerprintHex())
        .subscribe(
            hit ->
                resultFuture.complete(
                    AiProxyUpstreamResult.buffered(
                        200,
                        CONTENT_TYPE_JSON,
                        applyCacheMetadata(hit.reportBody(), true, hit.cachedAt()))),
            err -> {
              log.error("Screen RCA narrative MySQL cache lookup failed", err);
              resultFuture.complete(screenRcaCacheReadFailedResult());
            },
            () ->
                doProxyScreenNarrative(targetUrl, parsed, authorization, keyParts)
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

  private static AiProxyUpstreamResult screenRcaCacheReadFailedResult() {
    return AiProxyUpstreamResult.buffered(
        HTTP_DATABASE_ERROR, CONTENT_TYPE_JSON, ERROR_SCREEN_RCA_CACHE_READ_BODY);
  }

  private CompletionStage<AiProxyUpstreamResult> doProxyScreenNarrative(
      String targetUrl,
      ParsedScreenRcaPost parsed,
      String authorization,
      ScreenNarrativeCacheKeyParts keyParts) {
    ObjectNode working = parsed.bodyRoot().deepCopy();
    working.remove(REGENERATE_FIELD);
    String bodyToSend;
    try {
      bodyToSend = objectMapper.writeValueAsString(working);
    } catch (JsonProcessingException e) {
      return CompletableFuture.completedFuture(
          AiProxyUpstreamResult.buffered(
              HTTP_INTERNAL_ERROR, CONTENT_TYPE_JSON, ERROR_INTERNAL_BODY));
    }
    String projectId = keyParts.projectId();
    return upstream
        .executeProxy("POST", targetUrl, bodyToSend, authorization, projectId)
        .thenCompose(result -> persistScreenNarrativeReport(result, keyParts));
  }

  private CompletionStage<AiProxyUpstreamResult> persistScreenNarrativeReport(
      AiProxyUpstreamResult result, ScreenNarrativeCacheKeyParts keyParts) {
    if (!AiProxyUpstreamResult.isSuccessfulBuffered(result)) {
      return CompletableFuture.completedFuture(result);
    }
    String body = result.getBufferedBody();
    String withMeta = applyCacheMetadata(body, true, Instant.now());
    String mediaType = result.getMediaType();
    AiProxyUpstreamResult updated =
        AiProxyUpstreamResult.buffered(result.getStatusCode(), mediaType, withMeta);
    Completable putOp =
        screenRcaNarrativeCacheDao.put(
            keyParts.projectId(),
            keyParts.screenName(),
            keyParts.windowStartDateUtc(),
            keyParts.windowEndDateUtc(),
            keyParts.payloadFingerprintHex(),
            updated.getBufferedBody());
    CompletableFuture<AiProxyUpstreamResult> done = new CompletableFuture<>();
    putOp
        .andThen(io.reactivex.rxjava3.core.Single.just(updated))
        .subscribe(done::complete, done::completeExceptionally);
    return done;
  }

  private ScreenPostValidation validateScreenRcaNarrativePost(String body, String projectId) {
    if (body == null || body.isBlank()) {
      return new ScreenPostValidation.Invalid(
          badRequest(ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MESSAGE_BODY_REQUIRED));
    }
    if (projectId == null || projectId.isBlank()) {
      return new ScreenPostValidation.Invalid(
          badRequest(
              ServiceError.INCORRECT_OR_MISSING_HEADER_PARAMETERS, MESSAGE_PROJECT_HEADER_REQUIRED));
    }
    try {
      JsonNode tree = objectMapper.readTree(body);
      if (!(tree instanceof ObjectNode objectRoot)) {
        return new ScreenPostValidation.Invalid(
            badRequest(ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MESSAGE_BODY_JSON_OBJECT));
      }
      JsonNode screenNode = objectRoot.get(SCREEN_NAME_FIELD);
      if (screenNode == null || screenNode.asText().isBlank()) {
        return new ScreenPostValidation.Invalid(
            badRequest(
                ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MESSAGE_SCREEN_NAME_REQUIRED));
      }
      JsonNode startNode = objectRoot.get(START_FIELD);
      JsonNode endNode = objectRoot.get(END_FIELD);
      if (startNode == null
          || endNode == null
          || startNode.isNull()
          || endNode.isNull()
          || startNode.asText().isBlank()
          || endNode.asText().isBlank()) {
        return new ScreenPostValidation.Invalid(
            badRequest(
                ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MESSAGE_WINDOW_REQUIRED));
      }
      Instant windowStart;
      Instant windowEnd;
      try {
        windowStart = Instant.parse(startNode.asText().trim());
        windowEnd = Instant.parse(endNode.asText().trim());
      } catch (DateTimeParseException e) {
        return new ScreenPostValidation.Invalid(
            badRequest(
                ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MESSAGE_WINDOW_REQUIRED));
      }
      JsonNode payloadNode = objectRoot.get(ROOT_CAUSE_PAYLOAD_FIELD);
      if (payloadNode == null || payloadNode.isNull() || !payloadNode.isObject()) {
        return new ScreenPostValidation.Invalid(
            badRequest(
                ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS,
                MESSAGE_ROOT_CAUSE_PAYLOAD_REQUIRED));
      }
      String fingerprintHex;
      try {
        fingerprintHex = sha256HexOfJson(payloadNode);
      } catch (Exception e) {
        log.debug("Invalid rootCausePayload for fingerprint: {}", e.getMessage());
        return new ScreenPostValidation.Invalid(
            badRequest(
                ServiceError.INVALID_REQUEST_BODY, ServiceError.INVALID_REQUEST_BODY.getErrorMessage()));
      }
      boolean regenerate = isRegenerateRequested(objectRoot.get(REGENERATE_FIELD));
      LocalDate windowStartDateUtc = utcCalendarDate(windowStart);
      LocalDate windowEndDateUtc = utcCalendarDate(windowEnd);
      ScreenNarrativeCacheKeyParts keyParts =
          new ScreenNarrativeCacheKeyParts(
              projectId,
              screenNode.asText().trim(),
              windowStartDateUtc,
              windowEndDateUtc,
              fingerprintHex,
              regenerate);
      return new ScreenPostValidation.Valid(new ParsedScreenRcaPost(objectRoot, keyParts));
    } catch (Exception e) {
      log.debug("Invalid screen RCA narrative JSON: {}", e.getMessage());
      return new ScreenPostValidation.Invalid(
          badRequest(
              ServiceError.INVALID_REQUEST_BODY, ServiceError.INVALID_REQUEST_BODY.getErrorMessage()));
    }
  }

  /** UTC calendar date used for MySQL cache key (stable across intraday refresh for same day). */
  private static LocalDate utcCalendarDate(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).toLocalDate();
  }

  private static boolean isRegenerateRequested(JsonNode regenerateNode) {
    if (regenerateNode == null || regenerateNode.isNull()) {
      return false;
    }
    return regenerateNode.isBoolean() && regenerateNode.booleanValue();
  }

  private static AiProxyUpstreamResult badRequest(ServiceError error, String message) {
    int statusCode = error.getHttpStatusCode();
    String json = Error.of(error.getErrorCode(), message).toJsonString();
    return AiProxyUpstreamResult.buffered(statusCode, CONTENT_TYPE_JSON, json);
  }

  private String sha256HexOfJson(JsonNode node) throws JsonProcessingException {
    String canonical = objectMapper.writeValueAsString(node);
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
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
      }
      return objectMapper.writeValueAsString(node);
    } catch (Exception exception) {
      return body;
    }
  }

  private record ScreenNarrativeCacheKeyParts(
      String projectId,
      String screenName,
      LocalDate windowStartDateUtc,
      LocalDate windowEndDateUtc,
      String payloadFingerprintHex,
      boolean regenerate) {}

  private record ParsedScreenRcaPost(ObjectNode bodyRoot, ScreenNarrativeCacheKeyParts keyParts) {}

  private sealed interface ScreenPostValidation
      permits ScreenPostValidation.Valid, ScreenPostValidation.Invalid {
    record Valid(ParsedScreenRcaPost parsed) implements ScreenPostValidation {}

    record Invalid(AiProxyUpstreamResult response) implements ScreenPostValidation {}
  }
}
