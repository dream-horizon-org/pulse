package org.dreamhorizon.pulses3archiver.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import lombok.extern.slf4j.Slf4j;

/**
 * Minimal HTTP server exposing {@code /healthcheck} for ALB / docker-compose liveness probes.
 *
 * <p>The archiver itself is a pure Kafka consumer (no inbound traffic). This verticle exists
 * solely so the ASG / target group can verify the JVM is alive and Vert.x is initialized.
 *
 * <p>Liveness signal: the JVM is up and the event loop accepts connections. We deliberately
 * keep this lightweight — it does NOT probe Kafka or S3, because transient backend errors
 * shouldn't cause the ASG to terminate a healthy archiver mid-flush.
 */
@Slf4j
public class HealthCheckVerticle extends AbstractVerticle {

  public static final int DEFAULT_PORT = 8080;

  private final int port;
  private HttpServer server;

  public HealthCheckVerticle() {
    this(DEFAULT_PORT);
  }

  public HealthCheckVerticle(int port) {
    this.port = port;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    Router router = Router.router(vertx);
    router.get("/healthcheck").handler(ctx ->
        ctx.response()
            .putHeader("content-type", "text/plain")
            .setStatusCode(200)
            .end("OK"));
    router.get("/healthcheck.txt").handler(ctx ->
        ctx.response()
            .putHeader("content-type", "text/plain")
            .setStatusCode(200)
            .end("OK"));

    server = vertx.createHttpServer().requestHandler(router);
    server.listen(port)
        .onSuccess(s -> {
          log.info("[HealthCheck] Listening on :{}/healthcheck", port);
          startPromise.complete();
        })
        .onFailure(err -> {
          log.error("[HealthCheck] Failed to bind :{} — {}", port, err.getMessage());
          startPromise.fail(err);
        });
  }

  @Override
  public void stop() {
    if (server != null) {
      server.close();
    }
  }
}
