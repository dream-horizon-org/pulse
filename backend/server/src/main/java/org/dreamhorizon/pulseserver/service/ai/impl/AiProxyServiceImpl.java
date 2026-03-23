package org.dreamhorizon.pulseserver.service.ai.impl;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.service.ai.AiProxyService;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;

/**
 * HTTP client implementation for forwarding requests to the Pulse AI service using the dedicated
 * long-timeout Vert.x {@link WebClient} ({@link Constants#WEB_CLIENT_AI_PROXY}). Maps responses to
 * {@link AiProxyUpstreamResult}; the controller maps to JAX-RS.
 */
@Slf4j
public class AiProxyServiceImpl implements AiProxyService {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String PROJECT_HEADER = "X-Project-ID";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String CONTENT_TYPE_SSE = "text/event-stream";
  private static final String DEFAULT_AI_SERVICE_URL = "http://localhost:8000";

  /**
   * Upstream deadline / idle budget (ms) for AI proxy: per-request {@link HttpRequest#timeout}
   * plus {@link org.dreamhorizon.pulseserver.verticle.MainVerticle#getAiProxyWebClientOptions}.
   * Matches {@link org.dreamhorizon.pulseserver.resources.v1.ai.AiProxyController} {@code
   * @Timeout(120000)}.
   */
  public static final long AI_PROXY_UPSTREAM_TIMEOUT_MS = 120_000L;

  private final WebClient webClient;
  private final String aiServiceUrl;

  @Inject
  public AiProxyServiceImpl(
      @Named(Constants.WEB_CLIENT_AI_PROXY) WebClient webClient, ApplicationConfig config) {
    this(webClient, resolveAiUrl(config));
  }

  /**
   * For unit tests with a mock {@link WebClient}.
   */
  public AiProxyServiceImpl(WebClient webClient, String aiServiceUrl) {
    this.webClient = webClient;
    this.aiServiceUrl =
        aiServiceUrl != null && !aiServiceUrl.isBlank()
            ? aiServiceUrl
            : DEFAULT_AI_SERVICE_URL;
    log.info("AI proxy service initialized → {}", this.aiServiceUrl);
  }

  private static String resolveAiUrl(ApplicationConfig config) {
    String url = config.getAiServiceUrl();
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
    String targetUrl = buildTargetUrl(path, rawQuery);
    HttpRequest<Buffer> request = buildRequest(method, targetUrl, body, authorization, projectId);
    return execute(request, method, body);
  }

  private String buildTargetUrl(String path, String rawQuery) {
    boolean hasQuery = rawQuery != null && !rawQuery.isEmpty();
    return hasQuery
        ? aiServiceUrl + "/" + path + "?" + rawQuery
        : aiServiceUrl + "/" + path;
  }

  private HttpRequest<Buffer> buildRequest(
      String method,
      String targetUrl,
      String body,
      String authorization,
      String projectId) {
    HttpRequest<Buffer> req =
        switch (method) {
          case "POST" -> webClient.postAbs(targetUrl);
          case "PUT" -> webClient.putAbs(targetUrl);
          case "DELETE" -> webClient.deleteAbs(targetUrl);
          default -> webClient.getAbs(targetUrl);
        };
    req.putHeader(AUTHORIZATION_HEADER, authorization);
    if (projectId != null && !projectId.isBlank()) {
      req.putHeader(PROJECT_HEADER, projectId.trim());
    }
    boolean hasBody = body != null && !body.isEmpty();
    if (hasBody && ("POST".equals(method) || "PUT".equals(method))) {
      req.putHeader("Content-Type", CONTENT_TYPE_JSON);
    }
    req.timeout(AI_PROXY_UPSTREAM_TIMEOUT_MS);
    return req;
  }

  private CompletionStage<AiProxyUpstreamResult> execute(
      HttpRequest<Buffer> request, String method, String body) {
    boolean hasBody = body != null && !body.isEmpty();
    Single<HttpResponse<Buffer>> single;
    if (hasBody && ("POST".equals(method) || "PUT".equals(method))) {
      single = request.rxSendBuffer(Buffer.buffer(body));
    } else {
      single = request.rxSend();
    }
    CompletableFuture<AiProxyUpstreamResult> cf = new CompletableFuture<>();
    single.subscribe(
        resp -> {
          try {
            cf.complete(buildResult(resp));
          } catch (Exception e) {
            log.error("AI proxy failed building result: {}", e.getMessage());
            cf.complete(AiProxyUpstreamResult.badGateway());
          }
        },
        err -> {
          log.error("AI proxy error: {}", err.getMessage());
          cf.complete(AiProxyUpstreamResult.badGateway());
        });
    return cf;
  }

  private AiProxyUpstreamResult buildResult(HttpResponse<Buffer> response) {
    int statusCode = response.statusCode();
    String contentType = response.getHeader("Content-Type");
    if (contentType == null || contentType.isEmpty()) {
      contentType = CONTENT_TYPE_JSON;
    }
    boolean isSse = contentType.contains(CONTENT_TYPE_SSE);
    Buffer buf = response.body();
    byte[] bytes = buf != null ? buf.getBytes() : new byte[0];
    if (isSse) {
      return AiProxyUpstreamResult.streaming(
          statusCode, contentType, new ByteArrayInputStream(bytes));
    }
    return AiProxyUpstreamResult.buffered(
        statusCode, contentType, new String(bytes, StandardCharsets.UTF_8));
  }
}
