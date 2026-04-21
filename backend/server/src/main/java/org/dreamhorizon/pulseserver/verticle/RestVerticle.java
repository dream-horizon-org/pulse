package org.dreamhorizon.pulseserver.verticle;

import com.dream11.rest.AbstractRestVerticle;
import com.dream11.rest.ClassInjector;
import com.dream11.rest.filter.RequestResponseFilter;
import io.jsonwebtoken.JwtException;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.CorsHandler;
import io.vertx.rxjava3.ext.web.handler.ResponseContentTypeHandler;
import io.vertx.rxjava3.ext.web.handler.StaticHandler;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.filter.StreamingSafeLoggerFilter;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.alert.core.AlertEvaluationService;
import org.dreamhorizon.pulseserver.vertx.AiStreamingHttpClient;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

public class RestVerticle extends AbstractRestVerticle {
  private static final String PACKAGE_NAME = "org.dreamhorizon.pulseserver";
  private static final String JSON_CONTENT_TYPE = "application/json";

  protected RestVerticle(HttpServerOptions httpServerOptions) {
    super(PACKAGE_NAME, httpServerOptions);
  }

  @Override
  protected ClassInjector getInjector() {
    return GuiceInjector.getGuiceInjector();
  }

  @Override
  protected RequestResponseFilter getReqResFilter() {
    return new StreamingSafeLoggerFilter();
  }

  @Override
  protected List<Class<?>> getProviders() {
    List<Class<?>> providers = super.getProviders();
    providers.removeIf(clazz -> RequestResponseFilter.class.isAssignableFrom(clazz));
    providers.add(StreamingSafeLoggerFilter.class);
    return providers;
  }

  @Override
  protected Router getRouter() {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.route().handler(ResponseContentTypeHandler.create());
    router.route().handler(StaticHandler.create());

    AlertEvaluationService alertEvaluationService = GuiceInjector.getGuiceInjector().getInstance(AlertEvaluationService.class);
    alertEvaluationService.registerConsumers();

    final Set<String> allowedHeaders = new HashSet<>();
    allowedHeaders.add("x-requested-with");
    allowedHeaders.add("Access-Control-Allow-Origin");
    allowedHeaders.add("Access-Control-Allow-Methods");
    allowedHeaders.add("Access-Control-Allow-Headers");
    allowedHeaders.add("Access-Control-Allow-Credentials");
    allowedHeaders.add("origin");
    allowedHeaders.add("Content-Type");
    allowedHeaders.add("accept");
    allowedHeaders.add("X-PINGARUNER");
    allowedHeaders.add("Authorization");
    allowedHeaders.add("user-email");   // User email header for audit trails
    allowedHeaders.add("X-API-KEY");  // API key for authentication
    allowedHeaders.add("X-Project-ID"); // Project-level isolation support

    final Set<HttpMethod> allowedMethods = new HashSet<>();
    allowedMethods.add(HttpMethod.GET);
    allowedMethods.add(HttpMethod.POST);
    allowedMethods.add(HttpMethod.OPTIONS);
    allowedMethods.add(HttpMethod.DELETE);
    allowedMethods.add(HttpMethod.PATCH);
    allowedMethods.add(HttpMethod.PUT);
    router.route().handler(CorsHandler.create()
        .addRelativeOrigin(".*.")
        .allowCredentials(true)
        .allowedMethods(allowedMethods)
        .allowedHeaders(allowedHeaders));

    // Vert.x exact-match route for SSE proxying. Registered before the JAX-RS scanner mounts
    // resources, so it takes priority over AiProxyController's wildcard @Path("/{path:.*}").
    // This ordering is intentional: the JAX-RS path buffers the full response whereas this handler
    // streams chunks directly. Do not reorder without updating AiProxyController accordingly.
    router.post(Constants.AI_RUN_SSE_PATH).handler(this::handleAiStreamingProxy);

    return router;
  }

  /**
   * Native SSE proxy for the root agent. Stateless auth only - no {@code ProjectContext} / {@code
   * TenantContext} ThreadLocals.
   *
   * <p>JWT verification is offloaded to an IO thread via {@code Single.fromCallable} so that crypto
   * work never stalls the Vert.x event loop. The subsequent OpenFGA permission check is already
   * reactive. The returned {@link Disposable} is disposed when the client disconnects so the
   * subscription does not fire on a closed response.
   */
  void handleAiStreamingProxy(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader(Constants.HEADER_AUTHORIZATION);
    String projectId = ctx.request().getHeader(Constants.HEADER_PROJECT_ID);

    if (authHeader == null || !authHeader.startsWith(Constants.BEARER_PREFIX)) {
      respond(ctx, ServiceError.UNAUTHORISED.getHttpStatusCode(), "Missing auth token");
      return;
    }
    if (projectId == null || projectId.isBlank()) {
      respond(
          ctx,
          ServiceError.INCORRECT_OR_MISSING_HEADER_PARAMETERS.getHttpStatusCode(),
          "Missing X-Project-ID");
      return;
    }

    String token = authHeader.substring(Constants.BEARER_PREFIX.length()).trim();
    String trimmedProjectId = projectId.trim();

    JwtService jwtService = GuiceInjector.getGuiceInjector().getInstance(JwtService.class);
    OpenFgaService openFgaService =
        GuiceInjector.getGuiceInjector().getInstance(OpenFgaService.class);

    Disposable sub =
        Single.<String>fromCallable(
                () -> jwtService.verifyToken(token).getSubject())
            .subscribeOn(Schedulers.io())
            .flatMap(
                userId ->
                    openFgaService.checkPermission(
                        userId,
                        Constants.PERMISSION_CAN_VIEW,
                        Constants.RESOURCE_TYPE_PROJECT,
                        trimmedProjectId))
            .subscribe(
                allowed ->
                    vertx.runOnContext(
                        v -> {
                          if (!allowed) {
                            respond(ctx, ServiceError.FORBIDDEN.getHttpStatusCode(), "Access denied");
                            return;
                          }
                          proxyToAiService(ctx, authHeader, trimmedProjectId);
                        }),
                err ->
                    vertx.runOnContext(
                        v -> {
                          if (err instanceof JwtException) {
                            respond(
                                ctx, ServiceError.UNAUTHORISED.getHttpStatusCode(), "Invalid token");
                          } else {
                            respond(
                                ctx,
                                ServiceError.INTERNAL_SERVER_ERROR.getHttpStatusCode(),
                                "Auth check failed");
                          }
                        }));

    ctx.response().closeHandler(v -> sub.dispose());
  }

  /**
   * Forwards the POST body to the configured ADK {@code /run_sse} URL using the shared streaming
   * {@link io.vertx.core.http.HttpClient}. Uses {@link RequestOptions#setAbsoluteURI(String)} so
   * host, port, TLS, and path are resolved from one absolute URL — same idea as {@link
   * io.vertx.rxjava3.ext.web.client.WebClient#postAbs(String)} in {@link
   * org.dreamhorizon.pulseserver.service.ai.impl.AiUpstreamProxyExecutor}.
   */
  private void proxyToAiService(RoutingContext ctx, String authHeader, String projectId) {
    ApplicationConfig config = SharedDataUtils.get(vertx.getDelegate(), ApplicationConfig.class);
    String base = config.getAiServiceUrl();
    if (base == null || base.isBlank()) {
      respond(ctx, Status.SERVICE_UNAVAILABLE.getStatusCode(), "AI service URL is not configured");
      return;
    }
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    String absoluteUrl = normalizedBase + "/run_sse";
    try {
      URI.create(absoluteUrl);
    } catch (IllegalArgumentException e) {
      respond(ctx, Status.BAD_GATEWAY.getStatusCode(), "Bad AI service URL");
      return;
    }

    io.vertx.core.http.HttpClient httpClient =
        SharedDataUtils.get(
                vertx.getDelegate(), AiStreamingHttpClient.class, Constants.HTTP_CLIENT_AI_STREAMING)
            .client();

    String bodyStr = ctx.body().asString();
    Buffer bodyBuffer = bodyStr != null ? Buffer.buffer(bodyStr) : Buffer.buffer();

    HttpServerResponse response = ctx.response().getDelegate();

    httpClient
        .request(
            new RequestOptions()
                .setMethod(HttpMethod.POST)
                .setAbsoluteURI(absoluteUrl)
                .setTimeout(Constants.AI_UPSTREAM_TIMEOUT_MS))
        .compose(
            req -> {
              req.putHeader(Constants.HEADER_CONTENT_TYPE, JSON_CONTENT_TYPE);
              req.putHeader(Constants.HEADER_AUTHORIZATION, authHeader);
              req.putHeader(Constants.HEADER_PROJECT_ID, projectId);
              return req.send(bodyBuffer);
            })
        .onSuccess(
            upstreamResp -> {
              int status = upstreamResp.statusCode();

              if (status < 200 || status >= 300) {
                if (!response.ended()) {
                  response
                      .setStatusCode(status)
                      .putHeader(Constants.HEADER_CONTENT_TYPE, JSON_CONTENT_TYPE)
                      .end(jsonError("AI service returned " + status));
                }
                return;
              }

              response
                  .setStatusCode(Status.OK.getStatusCode())
                  .putHeader(
                      Constants.HEADER_CONTENT_TYPE, Constants.CONTENT_TYPE_TEXT_EVENT_STREAM)
                  .putHeader(Constants.HEADER_CACHE_CONTROL, Constants.SSE_PROXY_CACHE_CONTROL)
                  .putHeader(Constants.HEADER_CONNECTION, Constants.SSE_PROXY_CONNECTION)
                  .putHeader(
                      Constants.HEADER_X_ACCEL_BUFFERING, Constants.SSE_PROXY_X_ACCEL_BUFFERING)
                  .setChunked(true);

              upstreamResp.handler(
                  chunk -> {
                    if (!response.ended()) {
                      response.write(chunk);
                    }
                  });
              upstreamResp.endHandler(
                  v -> {
                    if (!response.ended()) {
                      response.end();
                    }
                  });
              upstreamResp.exceptionHandler(
                  err -> {
                    if (!response.ended()) {
                      response.end();
                    }
                  });
            })
        .onFailure(
            err -> {
              if (!response.ended()) {
                response
                    .setStatusCode(Status.BAD_GATEWAY.getStatusCode())
                    .putHeader(Constants.HEADER_CONTENT_TYPE, JSON_CONTENT_TYPE)
                    .end(jsonError("AI service unavailable"));
              }
            });
  }

  /**
   * Writes a JSON error response if the response has not already been ended. Used for error paths
   * in the SSE proxy where we cannot use JAX-RS exception handling.
   */
  private void respond(RoutingContext ctx, int statusCode, String message) {
    if (!ctx.response().ended()) {
      ctx.response()
          .setStatusCode(statusCode)
          .putHeader(Constants.HEADER_CONTENT_TYPE, JSON_CONTENT_TYPE)
          .end(jsonError(message));
    }
  }

  /**
   * Builds a minimal {@code {"error":"..."}} JSON string. Note: the rest of the API uses
   * {@code ServiceError.ExceptionResponseEntity} shape {@code {"error":{"code","message","cause"}}};
   * the SSE proxy uses this simpler shape because it bypasses JAX-RS serialisation.
   */
  private static String jsonError(String message) {
    return "{\"" + Constants.ERROR_KEY + "\":\"" + message.replace("\"", "\\\"") + "\"}";
  }
}
