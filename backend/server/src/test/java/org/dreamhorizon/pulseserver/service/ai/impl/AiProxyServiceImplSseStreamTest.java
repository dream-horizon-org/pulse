package org.dreamhorizon.pulseserver.service.ai.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration-style test: real Vert.x {@link WebClient} against a local HTTP server returning SSE.
 */
class AiProxyServiceImplSseStreamTest {

  private Vertx vertx;
  private HttpServer fakeAi;
  private int port;
  private AiProxyServiceImpl service;

  @BeforeEach
  void setUp() throws Exception {
    vertx = Vertx.vertx();
    CompletableFuture<Integer> listenReady = new CompletableFuture<>();
    fakeAi =
        vertx
            .createHttpServer()
            .requestHandler(
                req -> {
                  if ("/stream".equals(req.path())) {
                    req
                        .response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "text/event-stream; charset=utf-8")
                        .end("data: {\"chunk\":1}\n\n");
                  } else {
                    req.response().setStatusCode(404).end();
                  }
                });
    fakeAi.listen(
        0,
        "127.0.0.1",
        ar -> {
          if (ar.succeeded()) {
            listenReady.complete(ar.result().actualPort());
          } else {
            listenReady.completeExceptionally(ar.cause());
          }
        });
    port = listenReady.get(5, TimeUnit.SECONDS);

    WebClient webClient = WebClient.create(io.vertx.rxjava3.core.Vertx.newInstance(vertx));
    service = new AiProxyServiceImpl(webClient, "http://127.0.0.1:" + port);
  }

  @AfterEach
  void tearDown() throws Exception {
    CompletableFuture<Void> closed = new CompletableFuture<>();
    if (vertx != null) {
      vertx.close(ar -> closed.complete(null));
      closed.get(5, TimeUnit.SECONDS);
      vertx = null;
    }
    fakeAi = null;
  }

  @Test
  void forwardsSseChunksFromUpstream() throws Exception {
    AiProxyUpstreamResult result =
        service
            .proxy("GET", "stream", null, null, "Bearer test-token", null)
            .toCompletableFuture()
            .get(10, TimeUnit.SECONDS);

    assertThat(result.getStatusCode()).isEqualTo(200);
    assertThat(result.getMediaType()).contains("text/event-stream");
    assertThat(result.isBuffered()).isFalse();
    try (InputStream stream = result.getStreamBody()) {
      String body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(body).contains("data:").contains("chunk");
    }
  }
}
