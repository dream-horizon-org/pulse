package org.dreamhorizon.pulseserver.verticle;

import com.dream11.rest.AbstractRestVerticle;
import com.dream11.rest.ClassInjector;
import com.dream11.rest.filter.RequestResponseFilter;
import io.jsonwebtoken.Claims;
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
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.filter.StreamingSafeLoggerFilter;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.alert.core.AlertEvaluationService;
import org.dreamhorizon.pulseserver.vertx.AiStreamingHttpClient;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

public class RestVerticle extends AbstractRestVerticle {
  private static final String PACKAGE_NAME = "org.dreamhorizon.pulseserver";

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

    router.post("/v1/ai/run_sse").handler(this::handleAiStreamingProxy);

    return router;
  }

  /**
   * Native SSE proxy for the root agent. Stateless auth only - no {@code ProjectContext} / {@code
   * TenantContext} ThreadLocals.
   */
  void handleAiStreamingProxy(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");
    String projectId = ctx.request().getHeader("X-Project-ID");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      ctx.response()
          .setStatusCode(401)
          .putHeader("Content-Type", "application/json")
          .end("{\"error\":\"Missing auth token\"}");
      return;
    }
    if (projectId == null || projectId.isBlank()) {
      ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
          .end("{\"error\":\"Missing X-Project-ID\"}");
      return;
    }

    JwtService jwtService = GuiceInjector.getGuiceInjector().getInstance(JwtService.class);
    Claims claims;
    try {
      claims = jwtService.verifyToken(authHeader.substring("Bearer ".length()).trim());
    } catch (Exception e) {
      ctx.response()
          .setStatusCode(401)
          .putHeader("Content-Type", "application/json")
          .end("{\"error\":\"Invalid token\"}");
      return;
    }
    String userId = claims.getSubject();
    String trimmedProjectId = projectId.trim();

    OpenFgaService openFgaService =
        GuiceInjector.getGuiceInjector().getInstance(OpenFgaService.class);

    openFgaService
        .checkPermission(userId, "can_view", "project", trimmedProjectId)
        .subscribeOn(Schedulers.io())
        .subscribe(
            allowed ->
                vertx.runOnContext(
                    v -> {
                      if (!allowed) {
                        ctx.response()
                            .setStatusCode(403)
                            .putHeader("Content-Type", "application/json")
                            .end("{\"error\":\"Access denied\"}");
                        return;
                      }
                      proxyToAiService(ctx, authHeader, trimmedProjectId);
                    }),
            err ->
                vertx.runOnContext(
                    v ->
                        ctx.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end("{\"error\":\"Auth check failed\"}")));
  }

  private void proxyToAiService(RoutingContext ctx, String authHeader, String projectId) {
    ApplicationConfig config = SharedDataUtils.get(vertx.getDelegate(), ApplicationConfig.class);
    String base = config.getAiServiceUrl();
    if (base == null || base.isBlank()) {
      base = "http://localhost:8000";
    }
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;

    URI uri;
    try {
      uri = URI.create(normalizedBase + "/run_sse");
    } catch (Exception e) {
      ctx.response().setStatusCode(502).end("{\"error\":\"Bad AI service URL\"}");
      return;
    }

    int port = uri.getPort() > 0 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
    boolean ssl = "https".equals(uri.getScheme());
    // getPath() is safe here: normalizedBase always ends without a trailing slash, so the
    // constructed URI always has a non-empty path component ("/run_sse").

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
                .setHost(uri.getHost())
                .setPort(port)
                .setSsl(ssl)
                .setURI(uri.getPath())
                .setTimeout(120_000L))
        .compose(
            req -> {
              req.putHeader("Content-Type", "application/json");
              req.putHeader("Authorization", authHeader);
              req.putHeader("X-Project-ID", projectId);
              return req.send(bodyBuffer);
            })
        .onSuccess(
            upstreamResp -> {
              int status = upstreamResp.statusCode();

              if (status < 200 || status >= 300) {
                response
                    .setStatusCode(status)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"error\":\"AI service returned " + status + "\"}");
                return;
              }

              response
                  .setStatusCode(200)
                  .putHeader("Content-Type", "text/event-stream")
                  .putHeader("Cache-Control", "no-cache")
                  .putHeader("Connection", "keep-alive")
                  .putHeader("X-Accel-Buffering", "no")
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
                response.setStatusCode(502).end("{\"error\":\"AI service unavailable\"}");
              }
            });
  }
}
