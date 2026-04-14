package org.dreamhorizon.pulseserver.service.ai.impl;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;

/**
 * Forwards HTTP requests to the Pulse AI base URL (path + query, method, body, auth headers).
 */
@Slf4j
public final class AiUpstreamProxyExecutor {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String PROJECT_HEADER = "X-Project-ID";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String CONTENT_TYPE_SSE = "text/event-stream";

  /**
   * Per-request upstream timeout. Aligns with {@link
   * org.dreamhorizon.pulseserver.resources.v1.ai.AiProxyController} {@code @Timeout(120000)}.
   */
  public static final long UPSTREAM_TIMEOUT_MS = 120_000L;

  private final WebClient webClient;
  private final String aiServiceUrl;

  public AiUpstreamProxyExecutor(WebClient webClient, String aiServiceUrl) {
    this.webClient = webClient;
    this.aiServiceUrl = aiServiceUrl;
  }

  public String getAiServiceUrl() {
    return aiServiceUrl;
  }

  public String buildTargetUrl(String path, String rawQuery) {
    boolean hasQuery = rawQuery != null && !rawQuery.isEmpty();
    return hasQuery ? aiServiceUrl + "/" + path + "?" + rawQuery : aiServiceUrl + "/" + path;
  }

  public CompletionStage<AiProxyUpstreamResult> executeProxy(
      String method,
      String targetUrl,
      String body,
      String authorization,
      String projectId) {
    HttpRequest<Buffer> request = newAbsRequest(method, targetUrl);
    applyCommonHeaders(request, authorization, projectId);
    request.timeout(UPSTREAM_TIMEOUT_MS);
    return sendWithMethodAndBody(request, method, body)
        .map(this::buildResult)
        .doOnError(ex -> log.error("AI proxy error for {}: {}", targetUrl, ex.getMessage()))
        .onErrorReturnItem(AiProxyUpstreamResult.badGateway())
        .toCompletionStage();
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
}
