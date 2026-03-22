package org.dreamhorizon.pulseserver.service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Maybe;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.service.ai.AiProxyService;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;

/**
 * HTTP client implementation for forwarding requests to the Pulse AI service.
 * Returns {@link AiProxyUpstreamResult} (no JAX-RS types); the controller maps to
 * {@link jakarta.ws.rs.core.Response}.
 *
 * <p>POST {@code rca/report} uses read-through MySQL cache ({@link RcaReportCacheDao}), body
 * enrichment ({@code rootCausePayload} via {@link RootCauseService}), then upstream proxy.
 */
@Slf4j
public class AiProxyServiceImpl implements AiProxyService {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String PROJECT_HEADER = "X-Project-ID";
  private static final String SERVICE_KEY_HEADER = "X-Pulse-Service-Key";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String CONTENT_TYPE_SSE = "text/event-stream";
  private static final String DEFAULT_AI_SERVICE_URL = "http://localhost:8000";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final String RCA_REPORT_PATH = "rca/report";
  private static final String INTERACTION_NAME_FIELD = "interactionName";
  private static final String DATE_FIELD = "date";
  private static final int HTTP_INTERNAL_ERROR = 500;
  private static final String ERROR_INTERNAL_RCA = "{\"error\":\"Internal error generating RCA report\"}";
  private static final String ROOT_CAUSE_PAYLOAD_FIELD = "rootCausePayload";

  private final HttpClient httpClient;
  private final String aiServiceUrl;
  private final String serviceKey;
  private final ObjectMapper objectMapper;
  private final RootCauseService rootCauseService;
  private final RcaReportCacheDao rcaReportCacheDao;

  @Inject
  public AiProxyServiceImpl(
      ApplicationConfig config,
      ObjectMapper objectMapper,
      RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao) {
    this(
        newHttpClient(),
        normalizeAiServiceUrl(config.getAiServiceUrl()),
        config.getAiServiceKey() == null ? "" : config.getAiServiceKey(),
        objectMapper,
        rootCauseService,
        rcaReportCacheDao);
  }

  /**
   * For unit tests and direct wiring with a custom {@link HttpClient}. RCA-specific path behaves
   * like a plain proxy (no enrichment or DB cache).
   */
  public AiProxyServiceImpl(HttpClient httpClient, String aiServiceUrl) {
    this(
        httpClient,
        normalizeAiServiceUrl(aiServiceUrl),
        "",
        null,
        null,
        null);
  }

  /**
   * Package-private constructor for tests: inject mock {@link HttpClient} and RCA collaborators.
   */
  AiProxyServiceImpl(
      HttpClient httpClient,
      String aiServiceUrl,
      String serviceKey,
      ObjectMapper objectMapper,
      RootCauseService rootCauseService,
      RcaReportCacheDao rcaReportCacheDao) {
    this.httpClient = httpClient;
    this.aiServiceUrl = aiServiceUrl;
    this.serviceKey = serviceKey == null ? "" : serviceKey;
    this.objectMapper = objectMapper;
    this.rootCauseService = rootCauseService;
    this.rcaReportCacheDao = rcaReportCacheDao;
    log.info("AI proxy service initialized → {}", this.aiServiceUrl);
  }

  private static HttpClient newHttpClient() {
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(CONNECT_TIMEOUT)
        .build();
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
    HttpRequest request = buildRequest(method, path, rawQuery, body, authorization, projectId);
    return executeProxy(request);
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
      try {
        CompletionStage<Optional<String>> mysqlStage =
            maybeToCompletionStage(rcaReportCacheDao.get(keyParts.projectId(), keyParts.interactionName(), keyParts.date()));
        return withRcaErrorLogging(
            mysqlStage.thenCompose(
                maybeBody -> {
                  boolean hasMysqlHit = maybeBody.isPresent() && !maybeBody.get().isBlank();
                  if (hasMysqlHit) {
                    return CompletableFuture.completedFuture(
                        AiProxyUpstreamResult.buffered(
                            200,
                            CONTENT_TYPE_JSON,
                            applyCachedFlag(maybeBody.get(), true)));
                  }
                  return doEnrichAndProxyRca(targetUrl, body, authorization, projectId)
                      .thenApply(
                          result -> {
                            storeRcaReportInMysqlIfSuccess(result, keyParts);
                            return result;
                          });
                }));
      } catch (Throwable t) {
        log.warn("RCA MySQL cache lookup failed, falling back to AI: {}", t.getMessage());
        return withRcaErrorLogging(doEnrichAndProxyRca(targetUrl, body, authorization, projectId));
      }
    }

    return withRcaErrorLogging(doEnrichAndProxyRca(targetUrl, body, authorization, projectId));
  }

  private CompletionStage<AiProxyUpstreamResult> withRcaErrorLogging(
      CompletionStage<AiProxyUpstreamResult> stage) {
    return stage.exceptionally(
        ex -> {
          log.error("RCA report proxy failed", ex);
          return AiProxyUpstreamResult.buffered(
              HTTP_INTERNAL_ERROR, CONTENT_TYPE_JSON, ERROR_INTERNAL_RCA);
        });
  }

  private CompletionStage<AiProxyUpstreamResult> doEnrichAndProxyRca(
      String targetUrl,
      String body,
      String authorization,
      String projectId) {
    return enrichRcaBodyAsync(body, projectId)
        .thenCompose(
            enrichedBody -> {
              HttpRequest request =
                  buildRequestWithUrl("POST", targetUrl, enrichedBody, authorization, projectId);
              return executeProxy(request);
            });
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

  private static <T> CompletionStage<Optional<T>> maybeToCompletionStage(Maybe<T> maybe) {
    CompletableFuture<Optional<T>> future = new CompletableFuture<>();
    maybe.subscribe(
        value -> future.complete(Optional.of(value)),
        err -> future.complete(Optional.empty()),
        () -> future.complete(Optional.empty()));
    return future;
  }

  private record RcaCacheKeyParts(String projectId, String interactionName, LocalDate date) {}

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
      return Optional.of(new RcaCacheKeyParts(projectId, interactionName, date));
    } catch (Exception e) {
      log.debug("Unable to parse RCA cache key parts from body: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private CompletionStage<String> enrichRcaBodyAsync(String body, String projectId) {
    boolean isBodyMissing = body == null || body.isBlank();
    boolean isProjectMissing = projectId == null || projectId.isBlank();
    if (isBodyMissing || isProjectMissing) {
      return CompletableFuture.completedFuture(body);
    }

    try {
      ObjectNode root = (ObjectNode) objectMapper.readTree(body);
      JsonNode interactionNode = root.get(INTERACTION_NAME_FIELD);
      boolean isInteractionMissing = interactionNode == null || interactionNode.asText().isBlank();
      if (isInteractionMissing) {
        return CompletableFuture.completedFuture(body);
      }

      String interactionName = interactionNode.asText();
      LocalDate date = resolveDateFromNode(root.get(DATE_FIELD));

      CompletableFuture<String> future = new CompletableFuture<>();
      rootCauseService
          .getRootCause(projectId, interactionName, date)
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

  private HttpRequest buildRequest(
      String method,
      String path,
      String rawQuery,
      String body,
      String authorization,
      String projectId) {
    String targetUrl = buildTargetUrl(path, rawQuery);

    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(targetUrl))
            .header(AUTHORIZATION_HEADER, authorization);

    if (projectId != null && !projectId.isBlank()) {
      builder.header(PROJECT_HEADER, projectId.trim());
    }

    boolean hasServiceKey = !serviceKey.isEmpty();
    if (hasServiceKey) {
      builder.header(SERVICE_KEY_HEADER, serviceKey);
    }

    applyMethodAndBody(builder, method, body);
    return builder.build();
  }

  private HttpRequest buildRequestWithUrl(
      String method, String targetUrl, String body, String authorization, String projectId) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(targetUrl))
            .header(AUTHORIZATION_HEADER, authorization);

    if (projectId != null && !projectId.isBlank()) {
      builder.header(PROJECT_HEADER, projectId.trim());
    }

    boolean hasServiceKey = !serviceKey.isEmpty();
    if (hasServiceKey) {
      builder.header(SERVICE_KEY_HEADER, serviceKey);
    }

    applyMethodAndBody(builder, method, body);
    return builder.build();
  }

  private String buildTargetUrl(String path, String rawQuery) {
    boolean hasQuery = rawQuery != null && !rawQuery.isEmpty();
    return hasQuery
        ? aiServiceUrl + "/" + path + "?" + rawQuery
        : aiServiceUrl + "/" + path;
  }

  private void applyMethodAndBody(HttpRequest.Builder builder, String method, String body) {
    boolean hasBody = body != null && !body.isEmpty();
    switch (method) {
      case "POST":
      case "PUT":
        if (hasBody) {
          builder.header("Content-Type", CONTENT_TYPE_JSON);
        }
        HttpRequest.BodyPublisher publisher =
            hasBody ? BodyPublishers.ofString(body) : BodyPublishers.noBody();
        if ("POST".equals(method)) {
          builder.POST(publisher);
        } else {
          builder.PUT(publisher);
        }
        break;
      case "DELETE":
        builder.DELETE();
        break;
      default:
        builder.GET();
        break;
    }
  }

  private CompletionStage<AiProxyUpstreamResult> executeProxy(HttpRequest request) {
    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
        .thenApply(this::buildResult)
        .exceptionally(
            ex -> {
              log.error("AI proxy error for {}: {}", request.uri(), ex.getMessage());
              return AiProxyUpstreamResult.badGateway();
            });
  }

  private AiProxyUpstreamResult buildResult(HttpResponse<InputStream> response) {
    String contentType =
        response.headers().firstValue("Content-Type").orElse(CONTENT_TYPE_JSON);

    boolean isSse = contentType.contains(CONTENT_TYPE_SSE);
    if (isSse) {
      return buildStreamingResult(response, contentType);
    }
    return buildBufferedResult(response, contentType);
  }

  private AiProxyUpstreamResult buildStreamingResult(
      HttpResponse<InputStream> response, String contentType) {
    return AiProxyUpstreamResult.streaming(response.statusCode(), contentType, response.body());
  }

  private AiProxyUpstreamResult buildBufferedResult(
      HttpResponse<InputStream> response, String contentType) {
    try (InputStream is = response.body()) {
      String responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      return AiProxyUpstreamResult.buffered(response.statusCode(), contentType, responseBody);
    } catch (IOException e) {
      log.error("Failed to read AI service response: {}", e.getMessage());
      return AiProxyUpstreamResult.badGateway();
    }
  }

  private String applyCachedFlag(String body, boolean cached) {
    try {
      JsonNode node = objectMapper.readTree(body);
      if (node instanceof ObjectNode) {
        ((ObjectNode) node).put("cached", cached);
      }
      return objectMapper.writeValueAsString(node);
    } catch (Exception exception) {
      return body;
    }
  }
}
