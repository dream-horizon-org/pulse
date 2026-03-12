package org.dreamhorizon.pulseserver.resources.v1.ai;

import com.google.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import com.dream11.rest.annotation.Timeout;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.JwtService;

/**
 * JAX-RS resource that authenticates requests via JWT and proxies them
 * to the Pulse AI agent service. Supports SSE streaming for real-time responses.
 *
 * <p>Required because AbstractRestVerticle routes all non-swagger requests
 * directly to RESTEasy, bypassing the Vert.x Router.</p>
 */
@Slf4j
@Path("/v1/ai")
@Timeout(value = 120000, httpStatusCode = 504)
public class AiProxyResource {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String SERVICE_KEY_HEADER = "X-Pulse-Service-Key";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String CONTENT_TYPE_SSE = "text/event-stream";
  private static final String AI_SERVICE_URL_ENV = "AI_SERVICE_URL";
  private static final String AI_SERVICE_KEY_ENV = "AI_SERVICE_KEY";
  private static final String DEFAULT_AI_SERVICE_URL = "http://localhost:8000";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final int STREAM_BUFFER_SIZE = 1024;
  private static final int HTTP_BAD_GATEWAY = 502;

  private final JwtService jwtService;
  private final HttpClient httpClient;
  private final String aiServiceUrl;
  private final String serviceKey;

  @Inject
  public AiProxyResource(JwtService jwtService) {
    this.jwtService = jwtService;
    this.httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(CONNECT_TIMEOUT)
        .build();
    this.aiServiceUrl = System.getenv().getOrDefault(AI_SERVICE_URL_ENV, DEFAULT_AI_SERVICE_URL);
    this.serviceKey = System.getenv().getOrDefault(AI_SERVICE_KEY_ENV, "");
    log.info("AI proxy resource initialized → {}", aiServiceUrl);
  }

  @GET
  @Path("/{path:.*}")
  public CompletionStage<jakarta.ws.rs.core.Response> proxyGet(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @Context UriInfo uriInfo) {
    validateAuth(authorization);
    HttpRequest request = buildProxyRequest("GET", path, null, authorization, uriInfo);
    return executeProxy(request);
  }

  @POST
  @Path("/{path:.*}")
  public CompletionStage<jakarta.ws.rs.core.Response> proxyPost(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @Context UriInfo uriInfo,
      InputStream bodyStream) {
    validateAuth(authorization);
    String body = readBodySafe(bodyStream);
    HttpRequest request = buildProxyRequest("POST", path, body, authorization, uriInfo);
    return executeProxy(request);
  }

  @PUT
  @Path("/{path:.*}")
  public CompletionStage<jakarta.ws.rs.core.Response> proxyPut(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @Context UriInfo uriInfo,
      InputStream bodyStream) {
    validateAuth(authorization);
    String body = readBodySafe(bodyStream);
    HttpRequest request = buildProxyRequest("PUT", path, body, authorization, uriInfo);
    return executeProxy(request);
  }

  @DELETE
  @Path("/{path:.*}")
  public CompletionStage<jakarta.ws.rs.core.Response> proxyDelete(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @Context UriInfo uriInfo) {
    validateAuth(authorization);
    HttpRequest request = buildProxyRequest("DELETE", path, null, authorization, uriInfo);
    return executeProxy(request);
  }

  private String readBodySafe(InputStream bodyStream) {
    if (bodyStream == null) {
      return null;
    }
    try {
      byte[] bytes = bodyStream.readAllBytes();
      boolean isEmpty = bytes.length == 0;
      return isEmpty ? null : new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.debug("Failed to read request body: {}", e.getMessage());
      return null;
    }
  }

  private void validateAuth(String authorization) {
    boolean hasNoAuth = authorization == null || !authorization.startsWith(BEARER_PREFIX);
    if (hasNoAuth) {
      throw new WebApplicationException("Missing or invalid Authorization header", 401);
    }

    String token = authorization.substring(BEARER_PREFIX.length()).trim();
    boolean isTokenEmpty = token.isEmpty();
    if (isTokenEmpty) {
      throw new WebApplicationException("Empty authorization token", 401);
    }

    try {
      jwtService.verifyToken(token);
    } catch (Exception e) {
      log.debug("AI proxy: JWT verification failed: {}", e.getMessage());
      throw new WebApplicationException("Invalid or expired token", 401);
    }
  }

  private HttpRequest buildProxyRequest(
      String method, String path, String body, String authorization, UriInfo uriInfo) {
    String queryString = uriInfo.getRequestUri().getRawQuery();
    String targetUrl = aiServiceUrl + "/" + path;
    boolean hasQuery = queryString != null && !queryString.isEmpty();
    if (hasQuery) {
      targetUrl += "?" + queryString;
    }

    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(targetUrl))
        .header(AUTHORIZATION_HEADER, authorization);

    boolean hasServiceKey = serviceKey != null && !serviceKey.isEmpty();
    if (hasServiceKey) {
      builder.header(SERVICE_KEY_HEADER, serviceKey);
    }

    boolean hasBody = body != null && !body.isEmpty();
    switch (method) {
      case "POST":
        if (hasBody) {
          builder.header("Content-Type", CONTENT_TYPE_JSON)
              .POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
          builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        break;
      case "PUT":
        if (hasBody) {
          builder.header("Content-Type", CONTENT_TYPE_JSON)
              .PUT(HttpRequest.BodyPublishers.ofString(body));
        } else {
          builder.PUT(HttpRequest.BodyPublishers.noBody());
        }
        break;
      case "DELETE":
        builder.DELETE();
        break;
      default:
        builder.GET();
        break;
    }

    return builder.build();
  }

  private CompletionStage<jakarta.ws.rs.core.Response> executeProxy(HttpRequest request) {
    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
        .thenApply(this::buildResponse)
        .exceptionally(ex -> {
          log.error("AI proxy error for {}: {}", request.uri(), ex.getMessage());
          return jakarta.ws.rs.core.Response.status(HTTP_BAD_GATEWAY)
              .entity("{\"error\":\"AI service unavailable\"}")
              .type(CONTENT_TYPE_JSON)
              .build();
        });
  }

  private jakarta.ws.rs.core.Response buildResponse(HttpResponse<InputStream> response) {
    String contentType = response.headers()
        .firstValue("Content-Type")
        .orElse(CONTENT_TYPE_JSON);

    boolean isSse = contentType.contains(CONTENT_TYPE_SSE);
    if (isSse) {
      return buildStreamingResponse(response, contentType);
    }
    return buildBufferedResponse(response, contentType);
  }

  private jakarta.ws.rs.core.Response buildStreamingResponse(
      HttpResponse<InputStream> response, String contentType) {
    InputStream body = response.body();
    StreamingOutput stream = output -> {
      try (InputStream is = body) {
        byte[] buf = new byte[STREAM_BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = is.read(buf)) != -1) {
          output.write(buf, 0, bytesRead);
          output.flush();
        }
      }
    };

    return jakarta.ws.rs.core.Response.status(response.statusCode())
        .entity(stream)
        .type(contentType)
        .build();
  }

  private jakarta.ws.rs.core.Response buildBufferedResponse(
      HttpResponse<InputStream> response, String contentType) {
    try (InputStream is = response.body()) {
      String responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      return jakarta.ws.rs.core.Response.status(response.statusCode())
          .entity(responseBody)
          .type(contentType)
          .build();
    } catch (IOException e) {
      log.error("Failed to read AI service response: {}", e.getMessage());
      return jakarta.ws.rs.core.Response.status(HTTP_BAD_GATEWAY)
          .entity("{\"error\":\"AI service unavailable\"}")
          .type(CONTENT_TYPE_JSON)
          .build();
    }
  }
}
