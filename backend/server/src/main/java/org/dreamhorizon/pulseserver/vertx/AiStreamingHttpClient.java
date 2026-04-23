package org.dreamhorizon.pulseserver.vertx;

import static org.dreamhorizon.pulseserver.constant.Constants.HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE;
import static org.dreamhorizon.pulseserver.constant.Constants.HTTP_CLIENT_KEEP_ALIVE;
import static org.dreamhorizon.pulseserver.constant.Constants.HTTP_CONNECT_TIMEOUT;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import java.util.concurrent.TimeUnit;
import org.dreamhorizon.pulseserver.service.ai.impl.AiProxyServiceImpl;

/**
 * Holder for the core {@link HttpClient} used to proxy SSE to Pulse AI. Stored in {@link
 * SharedDataUtils} under {@link org.dreamhorizon.pulseserver.constant.Constants#HTTP_CLIENT_AI_STREAMING}
 * so lookup keys match {@link Object#getClass()} (never store the bare {@link HttpClient}
 * interface type).
 */
public record AiStreamingHttpClient(HttpClient client) {

  /**
   * Builds a keep-alive client with connect timeout at least 30s and idle timeout aligned with
   * {@link AiProxyServiceImpl#AI_PROXY_UPSTREAM_TIMEOUT_MS}.
   */
  public static AiStreamingHttpClient create(Vertx vertx, JsonObject webClientConfig) {
    int idleMs = (int) AiProxyServiceImpl.AI_PROXY_UPSTREAM_TIMEOUT_MS;
    int connectMs =
        Math.max(30_000, Integer.parseInt(webClientConfig.getString(HTTP_CONNECT_TIMEOUT)));
    HttpClient httpClient =
        vertx.createHttpClient(
            new HttpClientOptions()
                .setConnectTimeout(connectMs)
                .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
                .setIdleTimeout(idleMs)
                .setKeepAlive(Boolean.parseBoolean(webClientConfig.getString(HTTP_CLIENT_KEEP_ALIVE)))
                .setMaxPoolSize(
                    Integer.parseInt(webClientConfig.getString(HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE))));
    return new AiStreamingHttpClient(httpClient);
  }
}
