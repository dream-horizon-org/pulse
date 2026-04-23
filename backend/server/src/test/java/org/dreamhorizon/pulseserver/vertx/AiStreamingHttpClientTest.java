package org.dreamhorizon.pulseserver.vertx;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AiStreamingHttpClientTest {

  private Vertx vertx;

  @AfterEach
  void tearDown() throws Exception {
    if (vertx != null) {
      CompletableFuture<Void> done = new CompletableFuture<>();
      vertx.close(ar -> done.complete(null));
      done.get(5, TimeUnit.SECONDS);
      vertx = null;
    }
  }

  @Test
  void shouldCreateClientWithExpectedLifecycle() throws Exception {
    vertx = Vertx.vertx();
    JsonObject webClientConfig =
        new JsonObject()
            .put(Constants.HTTP_CONNECT_TIMEOUT, "5000")
            .put(Constants.HTTP_CLIENT_KEEP_ALIVE, "true")
            .put(Constants.HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE, "8");

    AiStreamingHttpClient holder = AiStreamingHttpClient.create(vertx, webClientConfig);

    assertThat(holder).isNotNull();
    assertThat(holder.client()).isNotNull();
    holder.client().close();
  }
}
