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
import org.dreamhorizon.pulseserver.service.rca.InteractionReportProcessor;
import org.dreamhorizon.pulseserver.service.rca.RcaCacheKey;
import org.dreamhorizon.pulseserver.service.rca.RcaJobDispatch;
import org.dreamhorizon.pulseserver.service.rca.RcaReportJobService;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

/**
 * POST {@code interaction-report}: read-through MySQL cache for {@link RcaType#INTERACTION_REPORT};
 * on miss (or regenerate) creates or joins an async job and returns {@code 202} with poll URL.
 */
@Slf4j
final class InteractionReportProxyHandler {

  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String ENTITY_KEY_FIELD = "entityKey";
  private static final String DATE_FIELD = "date";
  private static final String REGENERATE_FIELD = "regenerate";
  private static final int HTTP_ACCEPTED = 202;
  private static final int HTTP_INTERNAL_ERROR = 500;
  private static final int HTTP_DATABASE_ERROR = ServiceError.DATABASE_ERROR.getHttpStatusCode();
  private static final RcaType ENTITY_TYPE = RcaType.INTERACTION_REPORT;

  private final ObjectMapper objectMapper;
  private final RcaReportCacheDao rcaReportCacheDao;
  private final RcaReportJobService rcaReportJobService;
  private final InteractionReportProcessor interactionReportProcessor;

  InteractionReportProxyHandler(
      ObjectMapper objectMapper,
      RcaReportCacheDao rcaReportCacheDao,
      RcaReportJobService rcaReportJobService,
      InteractionReportProcessor interactionReportProcessor) {
    this.objectMapper = objectMapper;
    this.rcaReportCacheDao = rcaReportCacheDao;
    this.rcaReportJobService = rcaReportJobService;
    this.interactionReportProcessor = interactionReportProcessor;
  }

  CompletionStage<AiProxyUpstreamResult> handlePost(
      String rawQuery,
      String body,
      String authorization,
      String projectId,
      String createdByOrNull) {
    PostValidation validation = validatePost(body, projectId);
    if (validation instanceof PostValidation.Invalid invalid) {
      return CompletableFuture.completedFuture(invalid.response());
    }
    ParsedPost parsed = ((PostValidation.Valid) validation).parsed();
    KeyParts keyParts = parsed.keyParts();
    String tenantId = TenantContext.getCurrentTenantId().orElse(null);
    log.info(
        "Interaction report POST project={} entity={} date={} regenerate={} tenant={}",
        keyParts.projectId(),
        keyParts.entityKey(),
        keyParts.date(),
        keyParts.regenerate(),
        tenantId);
    if (keyParts.regenerate()) {
      return withErrorLogging(
          dispatchAsync(parsed, authorization, rawQuery, keyParts, createdByOrNull, tenantId));
    }
    return withErrorLogging(
        proxyAfterCacheLookup(
            keyParts, parsed, authorization, rawQuery, createdByOrNull, tenantId));
  }

  private CompletionStage<AiProxyUpstreamResult> withErrorLogging(
      CompletionStage<AiProxyUpstreamResult> stage) {
    return stage.exceptionally(
        ex -> {
          Throwable root = unwrapAsyncException(ex);
          log.error(
              "Interaction report proxy failed: type={} message={}",
              root.getClass().getName(),
              root.getMessage(),
              root);
          return AiProxyUpstreamResult.buffered(
              HTTP_INTERNAL_ERROR,
              CONTENT_TYPE_JSON,
              Error.of(ServiceError.INTERNAL_SERVER_ERROR.getErrorCode(), "Internal error generating interaction report")
                  .toJsonString());
        });
  }

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

  private CompletionStage<AiProxyUpstreamResult> proxyAfterCacheLookup(
      KeyParts keyParts,
      ParsedPost parsed,
      String authorization,
      String rawQuery,
      String createdByOrNull,
      String tenantIdOrNull) {
    CompletableFuture<AiProxyUpstreamResult> resultFuture = new CompletableFuture<>();
    rcaReportCacheDao
        .get(keyParts.projectId(), ENTITY_TYPE, keyParts.entityKey(), keyParts.date())
        .subscribeOn(Schedulers.io())
        .subscribe(
            hit ->
                resultFuture.complete(
                    AiProxyUpstreamResult.buffered(
                        200,
                        CONTENT_TYPE_JSON,
                        applyCacheMetadata(hit.reportBody(), true, hit.cachedAt()))),
            err -> {
              log.error("Interaction report cache lookup failed", err);
              resultFuture.complete(cacheReadFailedResult());
            },
            () ->
                dispatchAsync(
                        parsed, authorization, rawQuery, keyParts, createdByOrNull, tenantIdOrNull)
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

  private CompletionStage<AiProxyUpstreamResult> dispatchAsync(
      ParsedPost parsed,
      String authorization,
      String rawQuery,
      KeyParts keyParts,
      String createdByOrNull,
      String tenantIdOrNull) {
    CompletableFuture<AiProxyUpstreamResult> done = new CompletableFuture<>();
    RcaCacheKey key =
        new RcaCacheKey(
            keyParts.projectId(),
            ENTITY_TYPE,
            keyParts.entityKey(),
            keyParts.date(),
            keyParts.regenerate(),
            parsed.rawBody());
    rcaReportJobService
        .createOrGetJob(key, createdByOrNull)
        .subscribeOn(Schedulers.io())
        .subscribe(
            dispatch -> {
              if (dispatch.shouldEnqueueWorker()) {
                interactionReportProcessor.enqueueProcess(
                    dispatch.job(),
                    dispatch.requestBody(),
                    authorization,
                    rawQuery,
                    tenantIdOrNull);
              }
              done.complete(acceptedResult(dispatch));
            },
            done::completeExceptionally);
    return done;
  }

  private AiProxyUpstreamResult acceptedResult(RcaJobDispatch dispatch) {
    try {
      var job = dispatch.job();
      ObjectNode root = objectMapper.createObjectNode();
      root.set("jobId", TextNode.valueOf(job.jobId()));
      root.set("status", TextNode.valueOf(job.status().name()));
      root.set(
          "message",
          TextNode.valueOf(
              dispatch.shouldEnqueueWorker()
                  ? "Interaction report generation queued"
                  : "Joined existing interaction report job"));
      root.set("pollUrl", TextNode.valueOf("/v1/ai-rca/job/" + job.jobId()));
      root.put("isJoiningExistingJob", !dispatch.shouldEnqueueWorker());
      root.put("estimatedDurationSeconds", 60);
      if (job.createdAt() != null) {
        root.put("createdAt", DateTimeFormatter.ISO_INSTANT.format(job.createdAt()));
      }
      return AiProxyUpstreamResult.buffered(HTTP_ACCEPTED, CONTENT_TYPE_JSON, root.toString());
    } catch (Exception e) {
      log.warn("Failed to build interaction report 202 body: {}", e.getMessage());
      return AiProxyUpstreamResult.buffered(
          HTTP_INTERNAL_ERROR,
          CONTENT_TYPE_JSON,
          Error.of(ServiceError.INTERNAL_SERVER_ERROR.getErrorCode(), "Internal error generating interaction report")
              .toJsonString());
    }
  }

  private static AiProxyUpstreamResult cacheReadFailedResult() {
    return AiProxyUpstreamResult.buffered(
        HTTP_DATABASE_ERROR,
        CONTENT_TYPE_JSON,
        Error.of(
                ServiceError.DATABASE_ERROR.getErrorCode(),
                ServiceError.DATABASE_ERROR.getErrorMessage())
            .toJsonString());
  }

  private record KeyParts(
      String projectId, String entityKey, LocalDate date, boolean regenerate) {}

  private record ParsedPost(String rawBody, ObjectNode bodyRoot, KeyParts keyParts) {}

  private sealed interface PostValidation permits PostValidation.Valid, PostValidation.Invalid {
    record Valid(ParsedPost parsed) implements PostValidation {}

    record Invalid(AiProxyUpstreamResult response) implements PostValidation {}
  }

  private PostValidation validatePost(String body, String projectId) {
    if (body == null || body.isBlank()) {
      return new PostValidation.Invalid(
          badRequest(ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, "Request body is required"));
    }
    if (projectId == null || projectId.isBlank()) {
      return new PostValidation.Invalid(
          badRequest(
              ServiceError.INCORRECT_OR_MISSING_HEADER_PARAMETERS, "X-Project-ID header is required"));
    }
    try {
      JsonNode tree = objectMapper.readTree(body);
      if (!(tree instanceof ObjectNode objectRoot)) {
        return new PostValidation.Invalid(
            badRequest(ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, "Body must be a JSON object"));
      }
      String entityKey = extractEntityKey(objectRoot);
      if (entityKey == null || entityKey.isBlank()) {
        return new PostValidation.Invalid(
            badRequest(ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS, "entityKey is required"));
      }
      LocalDate date = resolveDateFromNode(objectRoot.get(DATE_FIELD));
      boolean regenerate = isRegenerateRequested(objectRoot.get(REGENERATE_FIELD));
      KeyParts keyParts = new KeyParts(projectId, entityKey, date, regenerate);
      return new PostValidation.Valid(new ParsedPost(body, objectRoot, keyParts));
    } catch (Exception e) {
      log.debug("Invalid interaction report JSON: {}", e.getMessage());
      return new PostValidation.Invalid(
          badRequest(
              ServiceError.INVALID_REQUEST_BODY, ServiceError.INVALID_REQUEST_BODY.getErrorMessage()));
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
    return AiProxyUpstreamResult.buffered(
        error.getHttpStatusCode(), CONTENT_TYPE_JSON, Error.of(error.getErrorCode(), message).toJsonString());
  }

  private static boolean isRegenerateRequested(JsonNode regenerateNode) {
    return regenerateNode != null
        && !regenerateNode.isNull()
        && regenerateNode.isBoolean()
        && regenerateNode.booleanValue();
  }

  private LocalDate resolveDateFromNode(JsonNode dateNode) {
    if (dateNode == null || dateNode.isNull()) {
      return LocalDate.now(ZoneOffset.UTC);
    }
    String dateValue = dateNode.asText();
    if (dateValue == null || dateValue.isBlank()) {
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
}
