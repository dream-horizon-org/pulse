package org.dreamhorizon.pulseserver.service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.Error;
import org.dreamhorizon.pulseserver.service.ai.AiProxyService;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;

/**
 * HTTP client implementation for forwarding requests to the Pulse AI service.
 * Returns {@link AiProxyUpstreamResult} (no JAX-RS types); the controller maps to
 * {@link jakarta.ws.rs.core.Response}.
 *
 * <p>POST {@code rca/report} uses read-through MySQL ({@link RcaReportCacheDao}; no time TTL),
 * optional {@code regenerate} to skip MySQL and force ClickHouse segment refresh, body enrichment
 * ({@code rootCausePayload} via {@link RootCauseService}), then upstream proxy.
 */
@Slf4j
public class AiProxyServiceImpl implements AiProxyService {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String PROJECT_HEADER = "X-Project-ID";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String CONTENT_TYPE_SSE = "text/event-stream";
  private static final String DEFAULT_AI_SERVICE_URL = "http://localhost:8000";
  private static final String RCA_REPORT_PATH = "rca/report";
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

  /**
   * Per-request upstream timeout. Aligns with {@link
   * org.dreamhorizon.pulseserver.resources.v1.ai.AiProxyController} {@code @Timeout(120000)}.
   */
  public static final long AI_PROXY_UPSTREAM_TIMEOUT_MS = 120_000L;

  private final WebClient webClient;
  private final String aiServiceUrl;
  private final ObjectMapper objectMapper;
  private final RootCauseService rootCauseService;
  private final RcaReportCacheDao rcaReportCacheDao;

  @Inject
  public AiProxyServiceImpl(
      @Named(Constants.WEB_CLIENT_AI_PROXY) WebClient webClient,
      ApplicationConfig config,
      ObjectMapper objectMapper,
      RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao) {
    this(
        webClient,
        normalizeAiServiceUrl(config.getAiServiceUrl()),
        objectMapper,
        rootCauseService,
        rcaReportCacheDao);
  }

  /**
   * For unit tests and simple wiring: plain proxy only (no RCA enrichment or MySQL cache).
   */
  public AiProxyServiceImpl(WebClient webClient, String aiServiceUrl) {
    this(webClient, normalizeAiServiceUrl(aiServiceUrl), null, null, null);
  }

  /**
   * Package-private constructor for tests: inject mock {@link WebClient} and RCA collaborators.
   */
  AiProxyServiceImpl(
      WebClient webClient,
      String aiServiceUrl,
      ObjectMapper objectMapper,
      RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao) {
    this.webClient = webClient;
    this.aiServiceUrl = aiServiceUrl;
    this.objectMapper = objectMapper;
    this.rootCauseService = rootCauseService;
    this.rcaReportCacheDao = rcaReportCacheDao;
    log.info("AI proxy service initialized → {}", this.aiServiceUrl);
  }

  private static String normalizeAiServiceUrl(String url) {
    return url != null && !url.isBlank() ? url : DEFAULT_AI_SERVICE_URL;
  }

  @Override
  public CompletionStage<AiProxyUpstreamResult> proxy(
      String method,
      String path,
      String rawQuery,
      String body,
      String authorization,
      String projectId) {
    boolean isRcaReportPost = "POST".equals(method) && RCA_REPORT_PATH.equals(path);
    boolean isRcaPipelineReady =
        objectMapper != null && rootCauseService != null && rcaReportCacheDao != null;
    if (isRcaReportPost && isRcaPipelineReady) {
      return proxyRcaReportPost(rawQuery, body, authorization, projectId);
    }
    String targetUrl = buildTargetUrl(path, rawQuery);
    return executeProxy(method, targetUrl, body, authorization, projectId);
  }

  private CompletionStage<AiProxyUpstreamResult> proxyRcaReportPost(
      String rawQuery,
      String body,
      String authorization,
      String projectId) {
    String targetUrl = buildTargetUrl(RCA_REPORT_PATH, rawQuery);
    Optional<RcaCacheKeyParts> keyPartsOpt = resolveRcaReportCacheKeyParts(body, projectId);

    if (keyPartsOpt.isPresent()) {
      RcaCacheKeyParts keyParts = keyPartsOpt.get();
      if (keyParts.regenerate()) {
        return withRcaErrorLogging(
            doEnrichAndProxyRca(targetUrl, body, authorization, projectId, true)
                .thenApply(
                    result -> finalizeSuccessfulRcaProxyResult(result, keyParts)));
      }
      return withRcaErrorLogging(
          proxyRcaAfterMysqlCacheLookup(keyParts, targetUrl, body, authorization, projectId));
    }

    return withRcaErrorLogging(doEnrichAndProxyRca(targetUrl, body, authorization, projectId, false));
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
                executeProxy("POST", targetUrl, enrichedBody, authorization, projectId));
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
                    .whenComplete(
                        (result, ex) -> {
                          if (ex != null) {
                            resultFuture.completeExceptionally(ex);
                            return;
                          }
                          AiProxyUpstreamResult finalized = finalizeSuccessfulRcaProxyResult(result, keyParts);
                          resultFuture.complete(finalized);
                        }));
    return resultFuture;
  }

  private static AiProxyUpstreamResult rcaCacheReadFailedResult() {
    return AiProxyUpstreamResult.buffered(
        HTTP_DATABASE_ERROR, CONTENT_TYPE_JSON, ERROR_RCA_CACHE_READ_BODY);
  }

  private void storeRcaReportInMysqlIfSuccess(
      AiProxyUpstreamResult result, RcaCacheKeyParts keyParts) {
    boolean isSuccess = result != null && result.getStatusCode() >= 200 && result.getStatusCode() < 300;
    if (!isSuccess) {
      return;
    }
    if (!result.isBuffered()) {
      return;
    }
    String entity = result.getBufferedBody();
    rcaReportCacheDao
        .put(keyParts.projectId(), keyParts.interactionName(), keyParts.date(), entity)
        .subscribe();
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
  private AiProxyUpstreamResult finalizeSuccessfulRcaProxyResult(
      AiProxyUpstreamResult result, RcaCacheKeyParts keyParts) {
    boolean isBufferedSuccess = isSuccessfulBufferedRcaResult(result);
    if (!isBufferedSuccess) {
      return result;
    }
    String withMeta = applyCacheMetadata(result.getBufferedBody(), true, Instant.now());
    String mediaType = result.getMediaType();
    AiProxyUpstreamResult updated =
        AiProxyUpstreamResult.buffered(result.getStatusCode(), mediaType, withMeta);
    storeRcaReportInMysqlIfSuccess(updated, keyParts);
    return updated;
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

  private String buildTargetUrl(String path, String rawQuery) {
    boolean hasQuery = rawQuery != null && !rawQuery.isEmpty();
    return hasQuery
        ? aiServiceUrl + "/" + path + "?" + rawQuery
        : aiServiceUrl + "/" + path;
  }

  private HttpRequest<Buffer> newAbsRequest(String method, String targetUrl) {
    return switch (method) {
      case "POST" -> webClient.postAbs(targetUrl);
      case "PUT" -> webClient.putAbs(targetUrl);
      case "DELETE" -> webClient.deleteAbs(targetUrl);
      default -> webClient.getAbs(targetUrl);
    };
  }

  private void applyCommonHeaders(
      HttpRequest<Buffer> request, String authorization, String projectId) {
    request.putHeader(AUTHORIZATION_HEADER, authorization);
    if (projectId != null && !projectId.isBlank()) {
      request.putHeader(PROJECT_HEADER, projectId.trim());
    }
  }

  private Single<HttpResponse<Buffer>> sendWithMethodAndBody(
      HttpRequest<Buffer> request, String method, String body) {
    boolean hasBody = body != null && !body.isEmpty();
    if (("POST".equals(method) || "PUT".equals(method)) && hasBody) {
      request.putHeader("Content-Type", CONTENT_TYPE_JSON);
      return request.rxSendBuffer(Buffer.buffer(body.getBytes(StandardCharsets.UTF_8)));
    }
    return request.rxSend();
  }

  private CompletionStage<AiProxyUpstreamResult> executeProxy(
      String method,
      String targetUrl,
      String body,
      String authorization,
      String projectId) {
    HttpRequest<Buffer> request = newAbsRequest(method, targetUrl);
    applyCommonHeaders(request, authorization, projectId);
    request.timeout(AI_PROXY_UPSTREAM_TIMEOUT_MS);
    return sendWithMethodAndBody(request, method, body)
        .map(this::buildResult)
        .doOnError(ex -> log.error("AI proxy error for {}: {}", targetUrl, ex.getMessage()))
        .onErrorReturnItem(AiProxyUpstreamResult.badGateway())
        .toCompletionStage();
  }

  private AiProxyUpstreamResult buildResult(HttpResponse<Buffer> response) {
    String contentType = response.getHeader("Content-Type");
    if (contentType == null || contentType.isBlank()) {
      contentType = CONTENT_TYPE_JSON;
    }
    boolean isSse = contentType.contains(CONTENT_TYPE_SSE);
    if (isSse) {
      return buildStreamingResult(response, contentType);
    }
    return buildBufferedResult(response, contentType);
  }

  private AiProxyUpstreamResult buildStreamingResult(
      HttpResponse<Buffer> response, String contentType) {
    Buffer buf = response.body();
    byte[] bytes = buf == null ? new byte[0] : buf.getBytes();
    return AiProxyUpstreamResult.streaming(
        response.statusCode(), contentType, new ByteArrayInputStream(bytes));
  }

  private AiProxyUpstreamResult buildBufferedResult(
      HttpResponse<Buffer> response, String contentType) {
    Buffer buf = response.body();
    String responseBody =
        buf == null ? "" : new String(buf.getBytes(), StandardCharsets.UTF_8);
    return AiProxyUpstreamResult.buffered(response.statusCode(), contentType, responseBody);
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
