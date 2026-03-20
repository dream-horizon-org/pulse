package org.dreamhorizon.pulseserver.service.ai.impl;

import com.google.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.service.ai.AiProxyService;

/**
 * HTTP client implementation for forwarding requests to the Pulse AI service.
 */
@Slf4j
public class AiProxyServiceImpl implements AiProxyService {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String PROJECT_HEADER = "X-Project-ID";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String CONTENT_TYPE_SSE = "text/event-stream";
  private static final String DEFAULT_AI_SERVICE_URL = "http://localhost:8000";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final int STREAM_BUFFER_SIZE = 1024;
  private static final int HTTP_BAD_GATEWAY = 502;

  private final HttpClient httpClient;
  private final String aiServiceUrl;

  @Inject
  public AiProxyServiceImpl(ApplicationConfig config) {
    this(
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build(),
        config.getAiServiceUrl());
  }

  /**
   * For unit tests and direct wiring with a custom {@link HttpClient}.
   */
  public AiProxyServiceImpl(HttpClient httpClient, String aiServiceUrl) {
    this.httpClient = httpClient;
    this.aiServiceUrl = aiServiceUrl != null && !aiServiceUrl.isBlank()
        ? aiServiceUrl : DEFAULT_AI_SERVICE_URL;
    log.info("AI proxy service initialized → {}", this.aiServiceUrl);
  }

  @Override
  public CompletionStage<Response> proxy(
      String method,
      String path,
      String rawQuery,
      String body,
      String authorization,
      String projectId) {
    HttpRequest request = buildRequest(method, path, rawQuery, body, authorization, projectId);
    return executeProxy(request);
  }

  private HttpRequest buildRequest(
      String method,
      String path,
      String rawQuery,
      String body,
      String authorization,
      String projectId) {
    String targetUrl = buildTargetUrl(path, rawQuery);

    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(targetUrl))
        .header(AUTHORIZATION_HEADER, authorization);

    if (projectId != null && !projectId.isBlank()) {
      builder.header(PROJECT_HEADER, projectId.trim());
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
        HttpRequest.BodyPublisher publisher = hasBody
            ? BodyPublishers.ofString(body)
            : BodyPublishers.noBody();
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

  private CompletionStage<Response> executeProxy(HttpRequest request) {
    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
        .thenApply(this::buildResponse)
        .exceptionally(ex -> {
          log.error("AI proxy error for {}: {}", request.uri(), ex.getMessage());
          return badGatewayResponse();
        });
  }

  private Response buildResponse(HttpResponse<InputStream> response) {
    String contentType = response.headers()
        .firstValue("Content-Type")
        .orElse(CONTENT_TYPE_JSON);

    boolean isSse = contentType.contains(CONTENT_TYPE_SSE);
    if (isSse) {
      return buildStreamingResponse(response, contentType);
    }
    return buildBufferedResponse(response, contentType);
  }

  private Response buildStreamingResponse(HttpResponse<InputStream> response, String contentType) {
    InputStream body = response.body();
    StreamingOutput stream = output -> pipeStream(body, output, STREAM_BUFFER_SIZE);

    return Response.status(response.statusCode())
        .entity(stream)
        .type(contentType)
        .build();
  }

  /**
   * Copies bytes from an input stream to a streaming JAX-RS output.
   */
  static void pipeStream(InputStream body, java.io.OutputStream output, int bufferSize)
      throws IOException {
    try (InputStream is = body) {
      byte[] buf = new byte[bufferSize];
      int bytesRead;
      while ((bytesRead = is.read(buf)) != -1) {
        output.write(buf, 0, bytesRead);
        output.flush();
      }
    }
  }

  private Response buildBufferedResponse(HttpResponse<InputStream> response, String contentType) {
    try (InputStream is = response.body()) {
      String responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      return Response.status(response.statusCode())
          .entity(responseBody)
          .type(contentType)
          .build();
    } catch (IOException e) {
      log.error("Failed to read AI service response: {}", e.getMessage());
      return badGatewayResponse();
    }
  }

  private Response badGatewayResponse() {
    return Response.status(HTTP_BAD_GATEWAY)
        .entity("{\"error\":\"AI service unavailable\"}")
        .type(CONTENT_TYPE_JSON)
        .build();
  }
}
