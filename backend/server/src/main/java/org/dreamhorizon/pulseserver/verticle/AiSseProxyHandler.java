package org.dreamhorizon.pulseserver.verticle;

import io.reactivex.rxjava3.disposables.Disposable;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.RoutingContext;
import jakarta.ws.rs.core.Response.Status;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.vertx.AiStreamingHttpClient;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

/**
 * Vert.x-native handler for {@code POST /v1/ai/run_sse}. Registered in {@link RestVerticle}
 * before the JAX-RS scanner so chunks stream without buffering.
 *
 * <p>Auth is stateless — no {@code ProjectContext} / {@code TenantContext} ThreadLocals. JWT
 * verification runs on an IO thread; the FGA check is reactive; both hop back to the event loop
 * via {@link Vertx#runOnContext} before touching the response.
 */
@Slf4j
class AiSseProxyHandler {

  private static final String JSON_CONTENT_TYPE = "application/json";

  private final Vertx vertx;
  private final VertxAuthChain authChain;

  AiSseProxyHandler(Vertx vertx) {
    this.vertx = vertx;
    JwtService jwtService = GuiceInjector.getGuiceInjector().getInstance(JwtService.class);
    OpenFgaService openFgaService = GuiceInjector.getGuiceInjector().getInstance(OpenFgaService.class);
    this.authChain = new VertxAuthChain(vertx, jwtService, openFgaService);
  }

  /** Entry point wired to the Vert.x route in {@link RestVerticle#getRouter()}. */
  void handle(RoutingContext ctx) {
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

    Disposable sub =
        authChain.authorize(
            ctx,
            token,
            trimmedProjectId,
            Constants.PERMISSION_CAN_VIEW,
            () -> proxyToAiService(ctx, authHeader, trimmedProjectId));

    ctx.response().closeHandler(v -> sub.dispose());
  }

  /**
   * Forwards the POST body to the configured ADK {@code /run_sse} URL using the shared {@link
   * AiStreamingHttpClient}. Chunks from the upstream {@code ReadStream<Buffer>} are forwarded
   * without buffering. Non-2xx responses are returned as JSON, not SSE.
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
                vertx.getDelegate(),
                AiStreamingHttpClient.class,
                Constants.HTTP_CLIENT_AI_STREAMING)
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

  private void respond(RoutingContext ctx, int statusCode, String message) {
    if (!ctx.response().ended()) {
      ctx.response()
          .setStatusCode(statusCode)
          .putHeader(Constants.HEADER_CONTENT_TYPE, JSON_CONTENT_TYPE)
          .end(jsonError(message));
    }
  }

  private static String jsonError(String message) {
    return "{\"" + Constants.ERROR_KEY + "\":\"" + message.replace("\"", "\\\"") + "\"}";
  }
}
