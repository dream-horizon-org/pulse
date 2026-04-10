package org.dreamhorizon.pulseserver.service.notification.provider;

import static org.dreamhorizon.pulseserver.constant.NotificationConstants.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.notification.TemplateService;
import org.dreamhorizon.pulseserver.service.notification.models.*;

@Slf4j
@Singleton
public class SlackNotificationProvider implements NotificationProvider {

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final TemplateService templateService;

  @Inject
  public SlackNotificationProvider(
      WebClient webClient, ObjectMapper objectMapper, TemplateService templateService) {
    this.webClient = webClient;
    this.objectMapper = objectMapper;
    this.templateService = templateService;
  }

  @Override
  public ChannelType getChannelType() {
    return ChannelType.SLACK;
  }

  @Override
  public Single<NotificationResult> send(
      NotificationMessage message, NotificationTemplate template) {
    long startTime = System.currentTimeMillis();

    if (!(message.getChannelConfig() instanceof SlackChannelConfig config)) {
      log.error("Expected SlackChannelConfig but got: {}",
          message.getChannelConfig() != null ? message.getChannelConfig().getClass().getSimpleName() : "null");
      return Single.just(
          NotificationResult.builder()
              .success(false)
              .errorMessage("Invalid Slack channel configuration")
              .permanentFailure(true)
              .build());
    }

    if (config.getAccessToken() == null || config.getAccessToken().isEmpty()) {
      return Single.just(
          NotificationResult.builder()
              .success(false)
              .errorMessage("Slack access token not configured")
              .permanentFailure(true)
              .build());
    }

    String channel = message.getRecipient();
    ObjectNode payload = SlackPayloadBuilder.buildPayload(
        objectMapper, templateService, template, message, config.getBotName(), config.getIconEmoji());
    payload.put(Slack.KEY_CHANNEL, channel);

    JsonObject jsonPayload;
    try {
      jsonPayload = new JsonObject(objectMapper.writeValueAsString(payload));
    } catch (Exception e) {
      log.error("Failed to serialize Slack payload", e);
      return Single.just(
          NotificationResult.builder()
              .success(false)
              .errorMessage("Failed to serialize Slack payload")
              .permanentFailure(true)
              .build());
    }

    return webClient
        .postAbs(Slack.API_URL)
        .putHeader(Slack.KEY_CONTENT_TYPE, Slack.CONTENT_TYPE_JSON)
        .putHeader(
            Slack.KEY_AUTHORIZATION, Slack.AUTHORIZATION_BEARER_PREFIX + config.getAccessToken())
        .timeout(HTTP_REQUEST_TIMEOUT_SECONDS * 1000L)
        .rxSendJsonObject(jsonPayload)
        .map(response -> parseSlackResponse(response.bodyAsString(), startTime))
        .onErrorReturn(
            error -> {
              long latency = System.currentTimeMillis() - startTime;
              log.error("Failed to send Slack notification", error);
              return NotificationResult.builder()
                  .success(false)
                  .errorMessage("HTTP request failed: " + error.getMessage())
                  .permanentFailure(false)
                  .latencyMs(latency)
                  .build();
            });
  }

  private NotificationResult parseSlackResponse(String responseBody, long startTime) {
    long latency = System.currentTimeMillis() - startTime;

    try {
      JsonNode response = objectMapper.readTree(responseBody);
      boolean ok = response.has(Slack.KEY_OK) && response.get(Slack.KEY_OK).asBoolean();

      if (ok) {
        String ts = response.has(Slack.KEY_TS) ? response.get(Slack.KEY_TS).asText() : null;
        return NotificationResult.builder()
            .success(true)
            .externalId(ts)
            .providerResponse(responseBody)
            .latencyMs(latency)
            .build();
      } else {
        String error =
            response.has(Slack.KEY_ERROR)
                ? response.get(Slack.KEY_ERROR).asText()
                : "Unknown error";

        return NotificationResult.builder()
            .success(false)
            .errorCode(error)
            .errorMessage("Slack API error: " + error)
            .permanentFailure(isPermanentError(error))
            .providerResponse(responseBody)
            .latencyMs(latency)
            .build();
      }
    } catch (Exception e) {
      log.error("Failed to parse Slack response", e);
      return NotificationResult.builder()
          .success(false)
          .errorMessage("Failed to parse Slack response: " + e.getMessage())
          .permanentFailure(false)
          .latencyMs(latency)
          .build();
    }
  }

  private boolean isPermanentError(String error) {
    return Slack.PERMANENT_ERROR_CODES.contains(error);
  }
}
