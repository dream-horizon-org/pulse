package org.dreamhorizon.pulseserver.service.alerts.grafana.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.constant.NotificationConstants.Slack;
import org.dreamhorizon.pulseserver.service.alerts.grafana.SlackPoster;

/**
 * Default {@link SlackPoster} backed by the shared Vert.x {@link WebClient}. POSTs to
 * {@code https://slack.com/api/chat.postMessage} with the supplied bot token.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class WebClientSlackPoster implements SlackPoster {

  private final WebClient webClient;

  @Override
  public Single<JsonObject> postMessage(String channel, String botToken, String text) {
    JsonObject payload = new JsonObject()
        .put(Slack.KEY_CHANNEL, channel)
        .put("text", text);

    return webClient.postAbs(Slack.API_URL)
        .putHeader(Slack.KEY_CONTENT_TYPE, Slack.CONTENT_TYPE_JSON)
        .putHeader(Slack.KEY_AUTHORIZATION, Slack.AUTHORIZATION_BEARER_PREFIX + botToken)
        .rxSendJsonObject(payload)
        .map(response -> {
          JsonObject body = response.bodyAsJsonObject();
          if (!body.getBoolean(Slack.KEY_OK, false)) {
            log.error("Slack chat.postMessage failed: {}", body.encode());
          } else {
            log.info("Slack chat.postMessage ok channel={}", channel);
          }
          return body;
        });
  }
}
