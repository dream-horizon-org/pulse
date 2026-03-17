package org.dreamhorizon.pulseserver.resources.v1.ai;

import com.dream11.rest.annotation.Timeout;
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
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
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
import org.dreamhorizon.pulseserver.service.JwtService;

/**
 * Authenticates requests via JWT and proxies them to the Pulse AI agent service.
 * Supports SSE streaming for real-time responses.
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
  private static final String DEFAULT_AI_SERVICE_URL = "http://localhost:8000";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final int STREAM_BUFFER_SIZE = 1024;
  private static final int HTTP_BAD_GATEWAY = 502;

  private final JwtService jwtService;
  private final HttpClient httpClient;
  private final String aiServiceUrl;
  private final String serviceKey;

  @Inject
  public AiProxyResource(JwtService jwtService, ApplicationConfig config) {
    this(jwtService,
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build(),
        config.getAiServiceUrl(),
        config.getAiServiceKey());
  }

  AiProxyResource(JwtService jwtService, HttpClient httpClient, String aiServiceUrl,
      String serviceKey) {
    this.jwtService = jwtService;
    this.httpClient = httpClient;
    this.aiServiceUrl = aiServiceUrl != null && !aiServiceUrl.isBlank()
        ? aiServiceUrl : DEFAULT_AI_SERVICE_URL;
    this.serviceKey = serviceKey != null ? serviceKey : "";
    log.info("AI proxy initialized → {}", this.aiServiceUrl);
  }

  @GET
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyGet(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @Context UriInfo uriInfo) {
    validateAuth(authorization);
    return executeProxy(buildRequest("GET", path, null, authorization, uriInfo));
  }

  @POST
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyPost(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @Context UriInfo uriInfo,
      InputStream bodyStream) {
    validateAuth(authorization);
    String body = readBodySafe(bodyStream);
    return executeProxy(buildRequest("POST", path, body, authorization, uriInfo));
  }

  @PUT
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyPut(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @Context UriInfo uriInfo,
      InputStream bodyStream) {
    validateAuth(authorization);
    String body = readBodySafe(bodyStream);
    return executeProxy(buildRequest("PUT", path, body, authorization, uriInfo));
  }

  @DELETE
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyDelete(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @Context UriInfo uriInfo) {
    validateAuth(authorization);
    return executeProxy(buildRequest("DELETE", path, null, authorization, uriInfo));
  }

  private void validateAuth(String authorization) {
    boolean isMissingBearer = authorization == null || !authorization.startsWith(BEARER_PREFIX);
    if (isMissingBearer) {
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
      log.debug("JWT verification failed: {}", e.getMessage());
      throw new WebApplicationException("Invalid or expired token", 401);
    }
  }

  private HttpRequest buildRequest(String method, String path, String body,
      String authorization, UriInfo uriInfo) {
    String targetUrl = buildTargetUrl(path, uriInfo);

    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(targetUrl))
        .header(AUTHORIZATION_HEADER, authorization);

    boolean hasServiceKey = !serviceKey.isEmpty();
    if (hasServiceKey) {
      builder.header(SERVICE_KEY_HEADER, serviceKey);
    }

    applyMethodAndBody(builder, method, body);
    return builder.build();
  }

  private String buildTargetUrl(String path, UriInfo uriInfo) {
    String queryString = uriInfo.getRequestUri().getRawQuery();
    boolean hasQuery = queryString != null && !queryString.isEmpty();
    return hasQuery
        ? aiServiceUrl + "/" + path + "?" + queryString
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

    return Response.status(response.statusCode())
        .entity(stream)
        .type(contentType)
        .build();
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
}
