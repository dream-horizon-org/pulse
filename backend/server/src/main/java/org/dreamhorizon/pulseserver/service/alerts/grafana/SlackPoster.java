package org.dreamhorizon.pulseserver.service.alerts.grafana;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;

/**
 * Thin transport layer for Slack {@code chat.postMessage}. Extracted as an interface so the
 * Grafana alert service can be unit-tested without mocking the concrete Vert.x {@code WebClient}
 * (Mockito 4.x cannot subclass it on Java 23). Production binding lives in {@code MainModule}.
 */
public interface SlackPoster {

  /**
   * POST {@code {"channel": ..., "text": ...}} to Slack with bearer auth.
   *
   * @return Slack's JSON response body. Caller should check {@code "ok"} field.
   */
  Single<JsonObject> postMessage(String channel, String botToken, String text);
}
