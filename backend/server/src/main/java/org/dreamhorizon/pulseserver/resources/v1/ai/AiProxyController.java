package org.dreamhorizon.pulseserver.resources.v1.ai;

import static org.dreamhorizon.pulseserver.resources.v1.ai.AiProxyResponseSupport.DEFAULT_STREAM_BUFFER_SIZE;
import static org.dreamhorizon.pulseserver.resources.v1.ai.AiProxyResponseSupport.rawQuery;
import static org.dreamhorizon.pulseserver.resources.v1.ai.AiProxyResponseSupport.readBodyUtf8;
import static org.dreamhorizon.pulseserver.resources.v1.ai.AiProxyResponseSupport.toJaxRsResponse;

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
import jakarta.ws.rs.core.UriInfo;
import java.io.InputStream;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.ai.AiProxyService;

/**
 * JAX-RS controller for the Pulse AI reverse proxy. Authenticates via JWT and delegates
 * upstream calls to {@link AiProxyService}. Maps upstream results to JAX-RS
 * {@link Response}, including {@link jakarta.ws.rs.core.StreamingOutput} for SSE streaming.
 * All methods require a valid JWT and {@code can_view} on the project ({@code X-Project-ID});
 * OpenFGA enforcement is handled by {@link org.dreamhorizon.pulseserver.filter.AuthorizationFilter}.
 */
@Slf4j
@Path("/v1/ai")
@Timeout(value = 120000, httpStatusCode = 504)
@RequiresPermission("can_view")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AiProxyController {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final JwtService jwtService;
  private final AiProxyService aiProxyService;

  @GET
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyGet(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @HeaderParam("X-Project-ID") String projectId,
      @Context UriInfo uriInfo) {
    validateAuth(authorization);
    return aiProxyService
        .proxy("GET", path, rawQuery(uriInfo), null, authorization, projectId)
        .thenApply(r -> toJaxRsResponse(r, DEFAULT_STREAM_BUFFER_SIZE));
  }

  @POST
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyPost(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @HeaderParam("X-Project-ID") String projectId,
      @Context UriInfo uriInfo,
      InputStream bodyStream) {
    validateAuth(authorization);
    String body = readBodyUtf8(bodyStream);
    return aiProxyService
        .proxy("POST", path, rawQuery(uriInfo), body, authorization, projectId)
        .thenApply(r -> toJaxRsResponse(r, DEFAULT_STREAM_BUFFER_SIZE));
  }

  @PUT
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyPut(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @HeaderParam("X-Project-ID") String projectId,
      @Context UriInfo uriInfo,
      InputStream bodyStream) {
    validateAuth(authorization);
    String body = readBodyUtf8(bodyStream);
    return aiProxyService
        .proxy("PUT", path, rawQuery(uriInfo), body, authorization, projectId)
        .thenApply(r -> toJaxRsResponse(r, DEFAULT_STREAM_BUFFER_SIZE));
  }

  @DELETE
  @Path("/{path:.*}")
  public CompletionStage<Response> proxyDelete(
      @PathParam("path") String path,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @HeaderParam("X-Project-ID") String projectId,
      @Context UriInfo uriInfo) {
    validateAuth(authorization);
    return aiProxyService
        .proxy("DELETE", path, rawQuery(uriInfo), null, authorization, projectId)
        .thenApply(r -> toJaxRsResponse(r, DEFAULT_STREAM_BUFFER_SIZE));
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
}
