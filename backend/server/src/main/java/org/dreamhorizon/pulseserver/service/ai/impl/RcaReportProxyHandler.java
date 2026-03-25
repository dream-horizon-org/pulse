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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.Error;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;

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

  private final AiUpstreamProxyExecutor upstream;
  private final ObjectMapper objectMapper;
  private final RootCauseService rootCauseService;
  private final RcaReportCacheDao rcaReportCacheDao;

  RcaReportProxyHandler(
      AiUpstreamProxyExecutor upstream,
      ObjectMapper objectMapper,
      RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao) {
    this.upstream = upstream;
    this.objectMapper = objectMapper;
    this.rootCauseService = rootCauseService;
    this.rcaReportCacheDao = rcaReportCacheDao;
  }

  CompletionStage<AiProxyUpstreamResult> handlePost(
      String rawQuery, String body, String authorization, String projectId) {
    String targetUrl = upstream.buildTargetUrl(RCA_REPORT_PATH, rawQuery);
    Optional<RcaCacheKeyParts> keyPartsOpt = resolveRcaReportCacheKeyParts(body, projectId);

    if (keyPartsOpt.isPresent()) {
      RcaCacheKeyParts keyParts = keyPartsOpt.get();
      if (keyParts.regenerate()) {
        return withRcaErrorLogging(
            doEnrichAndProxyRca(targetUrl, body, authorization, projectId, true)
                .thenCompose(result -> finalizeSuccessfulRcaProxyResult(result, keyParts)));
      }
      return withRcaErrorLogging(
          proxyRcaAfterMysqlCacheLookup(keyParts, targetUrl, body, authorization, projectId));
    }

    return withRcaErrorLogging(
        doEnrichAndProxyRca(targetUrl, body, authorization, projectId, false));
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
      String body,
      String authorization,
      String projectId,
      boolean forceRootCauseRefresh) {
    return enrichRcaBodyAsync(body, projectId, forceRootCauseRefresh)
        .thenCompose(
            enrichedBody ->
                upstream.executeProxy("POST", targetUrl, enrichedBody, authorization, projectId));
  }

  /**
   * Read-through MySQL cache: on hit returns cached JSON; on miss enriches and proxies upstream; on
   * DAO error completes with {@link ServiceError#DATABASE_ERROR} (does not call AI).
   */
  private CompletionStage<AiProxyUpstreamResult> proxyRcaAfterMysqlCacheLookup(
      RcaCacheKeyParts keyParts,
      String targetUrl,
      String body,
      String authorization,
      String projectId) {
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
                doEnrichAndProxyRca(targetUrl, body, authorization, projectId, false)
                    .thenCompose(result -> finalizeSuccessfulRcaProxyResult(result, keyParts))
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

  private Optional<RcaCacheKeyParts> resolveRcaReportCacheKeyParts(String body, String projectId) {
    boolean isProjectIdMissing = projectId == null || projectId.isBlank();
    boolean isBodyMissing = body == null || body.isBlank();
    if (isProjectIdMissing || isBodyMissing) {
      return Optional.empty();
    }
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode interactionNode = root.get(INTERACTION_NAME_FIELD);
      boolean isInteractionMissing = interactionNode == null || interactionNode.asText().isBlank();
      if (isInteractionMissing) {
        return Optional.empty();
      }
      String interactionName = interactionNode.asText();
      LocalDate date = resolveDateFromNode(root.get(DATE_FIELD));
      boolean regenerate = isRegenerateRequested(root.get(REGENERATE_FIELD));
      return Optional.of(new RcaCacheKeyParts(projectId, interactionName, date, regenerate));
    } catch (Exception e) {
      log.debug("Unable to parse RCA cache key parts from body: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private static boolean isRegenerateRequested(JsonNode regenerateNode) {
    if (regenerateNode == null || regenerateNode.isNull()) {
      return false;
    }
    if (regenerateNode.isBoolean()) {
      return regenerateNode.booleanValue();
    }
    if (regenerateNode.isTextual()) {
      return "true".equalsIgnoreCase(regenerateNode.asText().trim());
    }
    return false;
  }

  /**
   * On successful buffered RCA JSON, sets {@code cached} and {@code cachedAt} (for UI), persists to
   * MySQL, returns updated result.
   */
  private CompletionStage<AiProxyUpstreamResult> finalizeSuccessfulRcaProxyResult(
      AiProxyUpstreamResult result, RcaCacheKeyParts keyParts) {
    if (!isSuccessfulBufferedRcaResult(result)) {
      return CompletableFuture.completedFuture(result);
    }
    String withMeta = applyCacheMetadata(result.getBufferedBody(), true, Instant.now());
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

  private static boolean isSuccessfulBufferedRcaResult(AiProxyUpstreamResult result) {
    boolean statusOk =
        result != null
            && result.getStatusCode() >= 200
            && result.getStatusCode() < 300;
    boolean hasBody =
        result != null && result.isBuffered() && !result.getBufferedBody().isBlank();
    return statusOk && hasBody;
  }

  private CompletionStage<String> enrichRcaBodyAsync(
      String body, String projectId, boolean forceRootCauseRefresh) {
    boolean isBodyMissing = body == null || body.isBlank();
    boolean isProjectMissing = projectId == null || projectId.isBlank();
    if (isBodyMissing || isProjectMissing) {
      return CompletableFuture.completedFuture(body);
    }

    try {
      ObjectNode root = (ObjectNode) objectMapper.readTree(body);
      root.remove(REGENERATE_FIELD);
      JsonNode interactionNode = root.get(INTERACTION_NAME_FIELD);
      boolean isInteractionMissing = interactionNode == null || interactionNode.asText().isBlank();
      if (isInteractionMissing) {
        return CompletableFuture.completedFuture(body);
      }

      String interactionName = interactionNode.asText();
      LocalDate date = resolveDateFromNode(root.get(DATE_FIELD));

      CompletableFuture<String> future = new CompletableFuture<>();
      rootCauseService
          .getRootCause(projectId, interactionName, date, forceRootCauseRefresh)
          .subscribe(
              result -> {
                try {
                  JsonNode resultNode = objectMapper.valueToTree(result);
                  root.set(ROOT_CAUSE_PAYLOAD_FIELD, resultNode);
                  future.complete(objectMapper.writeValueAsString(root));
                } catch (Exception e) {
                  log.warn("Failed to serialize enriched RCA body: {}", e.getMessage());
                  future.complete(body);
                }
              },
              error -> {
                log.warn("Failed to fetch root-cause data for enrichment: {}", error.getMessage());
                future.complete(body);
              });
      return future;
    } catch (Exception e) {
      log.warn("Failed to parse RCA body for enrichment: {}", e.getMessage());
      return CompletableFuture.completedFuture(body);
    }
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
