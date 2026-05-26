package org.dreamhorizon.pulseserver.service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.Error;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.rca.RcaCacheKey;
import org.dreamhorizon.pulseserver.service.rca.RcaJobDispatch;
import org.dreamhorizon.pulseserver.service.rca.RcaReportJobService;
import org.dreamhorizon.pulseserver.service.rca.RcaReportProcessor;

/**
 * POST {@code rca/report}: read-through MySQL cache; on miss (or regenerate) creates or joins an
 * async job and returns {@code 202 Accepted} with {@code jobId} and {@code pollUrl}.
 */
@Slf4j
final class RcaReportProxyHandler {

  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String TYPE_FIELD = "rcaType";
  private static final String ENTITY_KEY_FIELD = "entityKey";
  private static final String DATE_FIELD = "date";
  private static final String REGENERATE_FIELD = "regenerate";
  private static final int HTTP_ACCEPTED = 202;
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
  private static final String MESSAGE_BODY_REQUIRED = "Request body is required";
  private static final String MESSAGE_PROJECT_HEADER_REQUIRED = "X-Project-ID header is required";
  private static final String MESSAGE_BODY_JSON_OBJECT = "RCA report body must be a JSON object";
  private static final String MESSAGE_ENTITY_REQUIRED = "entityKey is required";

  private final ObjectMapper objectMapper;
  private final RcaReportCacheDao rcaReportCacheDao;
  private final RcaReportJobService rcaReportJobService;
  private final RcaReportProcessor rcaReportProcessor;

  RcaReportProxyHandler(
      ObjectMapper objectMapper,
      RcaReportCacheDao rcaReportCacheDao,
      RcaReportJobService rcaReportJobService,
      RcaReportProcessor rcaReportProcessor) {
    this.objectMapper = objectMapper;
    this.rcaReportCacheDao = rcaReportCacheDao;
    this.rcaReportJobService = rcaReportJobService;
    this.rcaReportProcessor = rcaReportProcessor;
  }

  CompletionStage<AiProxyUpstreamResult> handlePost(
      String rawQuery,
      String body,
      String authorization,
      String projectId,
      String createdByOrNull) {
    RcaPostValidation validation = validateRcaReportPost(body, projectId);
    if (validation instanceof RcaPostValidation.Invalid invalid) {
      return CompletableFuture.completedFuture(invalid.response());
    }
    ParsedRcaPost parsed = ((RcaPostValidation.Valid) validation).parsed();
    RcaCacheKeyParts keyParts = parsed.keyParts();
    log.info(
        "RCA POST received project={} type={} entity={} date={} regenerate={}",
        keyParts.projectId(),
        keyParts.entityType(),
        keyParts.entityKey(),
        keyParts.date(),
        keyParts.regenerate());
    if (keyParts.regenerate()) {
      return withRcaErrorLogging(
          dispatchAsyncRca(parsed, authorization, rawQuery, keyParts, createdByOrNull));
    }
    return withRcaErrorLogging(
        proxyRcaAfterMysqlCacheLookup(
            keyParts, parsed, authorization, rawQuery, createdByOrNull));
  }

  private CompletionStage<AiProxyUpstreamResult> withRcaErrorLogging(
      CompletionStage<AiProxyUpstreamResult> stage) {
    return stage.exceptionally(
        ex -> {
          Throwable root = unwrapAsyncException(ex);
          log.error(
              "RCA report proxy failed (client sees BE1007): type={} message={}",
              root.getClass().getName(),
              root.getMessage(),
              root);
          return AiProxyUpstreamResult.buffered(
              HTTP_INTERNAL_ERROR, CONTENT_TYPE_JSON, ERROR_INTERNAL_RCA_BODY);
        });
  }

  /**
   * Prefer root cause so logs show the real DB/Rx failure, not only CompletionException.
   * Limit of 8 prevents infinite loops from circular exception causes (defensive).
   */
  private static Throwable unwrapAsyncException(Throwable ex) {
    Throwable t = ex;
    for (int i = 0; i < 8 && t != null; i++) {
      if (t instanceof java.util.concurrent.CompletionException && t.getCause() != null) {
        t = t.getCause();
        continue;
      }
      break;
    }
    return t != null ? t : ex;
  }

  private CompletionStage<AiProxyUpstreamResult> proxyRcaAfterMysqlCacheLookup(
      RcaCacheKeyParts keyParts,
      ParsedRcaPost parsed,
      String authorization,
      String rawQuery,
      String createdByOrNull) {
    CompletableFuture<AiProxyUpstreamResult> resultFuture = new CompletableFuture<>();
    log.info(
        "RCA MySQL cache lookup starting type={} entity={} date={}",
        keyParts.entityType(),
        keyParts.entityKey(),
        keyParts.date());
    rcaReportCacheDao
        .get(keyParts.projectId(), keyParts.entityType(), keyParts.entityKey(), keyParts.date())
        // Never block Vert.x / JAX-RS I/O thread on MySQL pool subscription.
        .subscribeOn(Schedulers.io())
        .subscribe(
            hit -> {
              log.info(
                  "RCA cache hit type={} entity={} date={}",
                  keyParts.entityType(),
                  keyParts.entityKey(),
                  keyParts.date());
              resultFuture.complete(
                  AiProxyUpstreamResult.buffered(
                      200,
                      CONTENT_TYPE_JSON,
                      applyCacheMetadata(hit.reportBody(), true, hit.cachedAt())));
            },
            err -> {
              log.error("RCA MySQL cache lookup failed", err);
              resultFuture.complete(rcaCacheReadFailedResult());
            },
            () -> {
              log.info(
                  "RCA cache miss, async job path type={} entity={} date={}",
                  keyParts.entityType(),
                  keyParts.entityKey(),
                  keyParts.date());
              dispatchAsyncRca(parsed, authorization, rawQuery, keyParts, createdByOrNull)
                  .whenComplete(
                      (result, ex) -> {
                        if (ex != null) {
                          resultFuture.completeExceptionally(ex);
                        } else {
                          resultFuture.complete(result);
                        }
                      });
            });
    return resultFuture;
  }

  private CompletionStage<AiProxyUpstreamResult> dispatchAsyncRca(
      ParsedRcaPost parsed,
      String authorization,
      String rawQuery,
      RcaCacheKeyParts keyParts,
      String createdByOrNull) {
    CompletableFuture<AiProxyUpstreamResult> done = new CompletableFuture<>();
    RcaCacheKey key =
        new RcaCacheKey(
            keyParts.projectId(),
            keyParts.entityType(),
            keyParts.entityKey(),
            keyParts.date(),
            keyParts.regenerate(),
            parsed.rawBody());
    log.info(
        "RCA createOrGetJob starting type={} entity={} date={}",
        keyParts.entityType(),
        keyParts.entityKey(),
        keyParts.date());
    rcaReportJobService
        .createOrGetJob(key, createdByOrNull)
        .subscribeOn(Schedulers.io())
        .subscribe(
            dispatch -> {
              AiProxyUpstreamResult response =
                  acceptedResult(dispatch, keyParts.projectId());
              log.info(
                  "RCA returning {} project={} jobId={} enqueueWorker={} type={} entity={}",
                  HTTP_ACCEPTED,
                  keyParts.projectId(),
                  dispatch.job().jobId(),
                  dispatch.shouldEnqueueWorker(),
                  keyParts.entityType(),
                  keyParts.entityKey());
              if (dispatch.shouldEnqueueWorker()) {
                rcaReportProcessor.enqueueProcess(
                    dispatch.job(),
                    dispatch.requestBody(),
                    dispatch.forceRootCauseRefresh(),
                    authorization,
                    rawQuery);
              }
              done.complete(response);
            },
            done::completeExceptionally);
    return done;
  }

  private AiProxyUpstreamResult acceptedResult(RcaJobDispatch dispatch, String projectId) {
    try {
      var job = dispatch.job();
      log.debug("RCA 202 response body project={} jobId={}", projectId, job.jobId());
      ObjectNode root = objectMapper.createObjectNode();
      root.set("jobId", TextNode.valueOf(job.jobId()));
      root.set("status", TextNode.valueOf(job.status().name()));
      root.set(
          "message",
          TextNode.valueOf(
              dispatch.shouldEnqueueWorker()
                  ? "Report generation queued"
                  : "Joined existing RCA generation job"));
      root.set("pollUrl", TextNode.valueOf("/v1/ai-rca/job/" + job.jobId()));
      root.put("isJoiningExistingJob", !dispatch.shouldEnqueueWorker());
      root.put("estimatedDurationSeconds", 180);
      if (job.createdAt() != null) {
        root.put("createdAt", DateTimeFormatter.ISO_INSTANT.format(job.createdAt()));
      }
      if (job.startedAt() != null) {
        root.put("startedAt", DateTimeFormatter.ISO_INSTANT.format(job.startedAt()));
      }
      return AiProxyUpstreamResult.buffered(HTTP_ACCEPTED, CONTENT_TYPE_JSON, root.toString());
    } catch (Exception e) {
      log.warn("Failed to build RCA 202 body (project={}): {}", projectId, e.getMessage());
      return AiProxyUpstreamResult.buffered(
          HTTP_INTERNAL_ERROR, CONTENT_TYPE_JSON, ERROR_INTERNAL_RCA_BODY);
    }
  }

  private static AiProxyUpstreamResult rcaCacheReadFailedResult() {
    return AiProxyUpstreamResult.buffered(
        HTTP_DATABASE_ERROR, CONTENT_TYPE_JSON, ERROR_RCA_CACHE_READ_BODY);
  }

  private record RcaCacheKeyParts(
      String projectId, RcaType entityType, String entityKey, LocalDate date, boolean regenerate) {}

  /** Parsed JSON body and cache key after {@link #validateRcaReportPost} succeeds. */
  private record ParsedRcaPost(String rawBody, ObjectNode bodyRoot, RcaCacheKeyParts keyParts) {}

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

      RcaType type = extractRcaType(objectRoot);
      boolean typeMissing = type == null;
      if (typeMissing) {
        AiProxyUpstreamResult errorResponse =
            badRequest(
                ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, "rcaType is required");
        return new RcaPostValidation.Invalid(errorResponse);
      }

      String entityKey = extractEntityKey(objectRoot);
      boolean entityMissing = entityKey == null || entityKey.isBlank();
      if (entityMissing) {
        AiProxyUpstreamResult errorResponse =
            badRequest(
                ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, MESSAGE_ENTITY_REQUIRED);
        return new RcaPostValidation.Invalid(errorResponse);
      }

      if (type == RcaType.SCREEN || type == RcaType.SCREEN_V2) {
        JsonNode startNode = objectRoot.get("start");
        JsonNode endNode = objectRoot.get("end");
        if (startNode == null
            || endNode == null
            || !startNode.isTextual()
            || !endNode.isTextual()
            || startNode.asText().isBlank()
            || endNode.asText().isBlank()) {
          AiProxyUpstreamResult errorResponse =
              badRequest(
                  ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS,
                  "start and end (ISO-8601 instants) are required for screen RCA");
          return new RcaPostValidation.Invalid(errorResponse);
        }
        try {
          Instant.parse(startNode.asText().trim());
          Instant.parse(endNode.asText().trim());
        } catch (DateTimeParseException e) {
          AiProxyUpstreamResult errorResponse =
              badRequest(
                  ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS,
                  "start and end must be valid ISO-8601 instants");
          return new RcaPostValidation.Invalid(errorResponse);
        }
      }

      LocalDate date = resolveDateFromNode(objectRoot.get(DATE_FIELD));
      boolean regenerate = isRegenerateRequested(objectRoot.get(REGENERATE_FIELD));

      RcaCacheKeyParts keyParts = new RcaCacheKeyParts(projectId, type, entityKey, date, regenerate);
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

  private static RcaType extractRcaType(ObjectNode objectRoot) {
    JsonNode typeNode = objectRoot.get(TYPE_FIELD);
    if (typeNode == null || typeNode.isNull()) {
      return null;
    }
    String typeStr = typeNode.asText().trim().toUpperCase();
    try {
      return RcaType.valueOf(typeStr);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid RCA type '{}'", typeStr);
      return null;
    }
  }

  private static String extractEntityKey(ObjectNode objectRoot) {
    JsonNode entityNode = objectRoot.get(ENTITY_KEY_FIELD);
    if (entityNode == null || entityNode.isNull()) {
      return null;
    }
    return entityNode.asText().trim();
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
