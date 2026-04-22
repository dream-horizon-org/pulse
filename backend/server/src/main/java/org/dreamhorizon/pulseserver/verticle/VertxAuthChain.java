package org.dreamhorizon.pulseserver.verticle;

import io.jsonwebtoken.JwtException;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;

/**
 * Reusable async auth chain for native Vert.x handlers: JWT verify → OpenFGA permission check →
 * hop back to event loop → invoke callback.
 *
 * <p>Designed to be shared between {@link AiSseProxyHandler} and future Vert.x Router auth
 * handlers (replacing {@code AuthorizationFilter}). The only difference between callers is
 * {@code onAllowed}: SSE passes {@code () -> proxyToAiService(...)}, Router handlers pass
 * {@code ctx::next}.
 *
 * <p>JWT verification runs on the IO scheduler (CPU-bound crypto). {@link Vertx#runOnContext} is
 * always used before touching the response or calling {@code onAllowed} — Vert.x responses must
 * be written from the event loop thread that owns the connection.
 */
@Slf4j
class VertxAuthChain {

  private static final String JSON_CONTENT_TYPE = "application/json";

  private final Vertx vertx;
  private final JwtService jwtService;
  private final OpenFgaService openFgaService;

  VertxAuthChain(Vertx vertx, JwtService jwtService, OpenFgaService openFgaService) {
    this.vertx = vertx;
    this.jwtService = jwtService;
    this.openFgaService = openFgaService;
  }

  /**
   * Verifies {@code token}, checks {@code permission} on {@code projectId}, then calls
   * {@code onAllowed} on the event loop. Any error or deny aborts with an appropriate JSON
   * response. Returns a {@link Disposable} — callers must dispose it on client disconnect.
   *
   * @param permission the OpenFGA relation to check (e.g., {@link Constants#PERMISSION_CAN_VIEW})
   * @param onAllowed  called on the event loop when auth passes; SSE callers pass a proxy
   *                   invocation, Router handlers pass {@code ctx::next}
   */
  Disposable authorize(
      RoutingContext ctx, String token, String projectId, String permission, Runnable onAllowed) {
    return Single.<String>fromCallable(() -> jwtService.verifyToken(token).getSubject())
        .subscribeOn(Schedulers.io())
        .flatMap(
            userId ->
                openFgaService.checkPermission(
                    userId, permission, Constants.RESOURCE_TYPE_PROJECT, projectId))
        .subscribe(
            allowed ->
                vertx.runOnContext(
                    v -> {
                      if (!allowed) {
                        rejectWith(ctx, ServiceError.FORBIDDEN);
                        return;
                      }
                      onAllowed.run();
                    }),
            err ->
                vertx.runOnContext(
                    v -> {
                      if (err instanceof JwtException) {
                        rejectWith(ctx, ServiceError.UNAUTHORISED);
                      } else {
                        log.error("Auth check failed", err);
                        rejectWith(ctx, ServiceError.INTERNAL_SERVER_ERROR);
                      }
                    }));
  }

  private void rejectWith(RoutingContext ctx, ServiceError serviceError) {
    if (!ctx.response().ended()) {
      ctx.response()
          .setStatusCode(serviceError.getHttpStatusCode())
          .putHeader(Constants.HEADER_CONTENT_TYPE, JSON_CONTENT_TYPE)
          .end(serviceError.toJson());
    }
  }
}
