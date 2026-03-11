package org.dreamhorizon.pulseserver.service.notification.provider;

import static org.dreamhorizon.pulseserver.constant.NotificationConstants.*;

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
public class SlackWebhookNotificationProvider implements NotificationProvider {

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final TemplateService templateService;

  @Inject
  public SlackWebhookNotificationProvider(
      WebClient webClient, ObjectMapper objectMapper, TemplateService templateService) {
    this.webClient = webClient;
    this.objectMapper = objectMapper;
    this.templateService = templateService;
  }

  @Override
  public ChannelType getChannelType() {
    return ChannelType.SLACK_WEBHOOK;
  }

  @Override
  public Single<NotificationResult> send(
      NotificationMessage message, NotificationTemplate template) {
    long startTime = System.currentTimeMillis();

    String webhookUrl = message.getRecipient();
    if (webhookUrl == null || webhookUrl.isBlank()) {
      return Single.just(
          NotificationResult.builder()
              .success(false)
              .errorMessage("Webhook URL not provided in recipient")
              .permanentFailure(true)
              .build());
    }

    SlackWebhookChannelConfig config;
    if (message.getChannelConfig() instanceof SlackWebhookChannelConfig webhookConfig) {
      config = webhookConfig;
    } else {
      log.warn("Expected SlackWebhookChannelConfig but got: {}, using defaults",
          message.getChannelConfig() != null ? message.getChannelConfig().getClass().getSimpleName() : "null");
      config = SlackWebhookChannelConfig.builder().build();
    }

    ObjectNode payload = SlackPayloadBuilder.buildPayload(
        objectMapper, templateService, template, message,
        config.getBotName(), config.getIconEmoji());

    JsonObject jsonPayload;
    try {
      jsonPayload = new JsonObject(objectMapper.writeValueAsString(payload));
    } catch (Exception e) {
      log.error("Failed to serialize Slack webhook payload", e);
      return Single.just(
          NotificationResult.builder()
              .success(false)
              .errorMessage("Failed to serialize webhook payload")
              .permanentFailure(true)
              .build());
    }

    return webClient
        .postAbs(webhookUrl)
        .putHeader(Slack.KEY_CONTENT_TYPE, Slack.CONTENT_TYPE_JSON)
        .timeout(HTTP_REQUEST_TIMEOUT_SECONDS * 1000L)
        .rxSendJsonObject(jsonPayload)
        .map(
            response -> {
              long latency = System.currentTimeMillis() - startTime;
              int statusCode = response.statusCode();
              String body = response.bodyAsString();

              if (statusCode == 200 && "ok".equals(body)) {
                return NotificationResult.builder()
                    .success(true)
                    .providerResponse(body)
                    .latencyMs(latency)
                    .build();
              }

              boolean permanent = statusCode == 403 || statusCode == 404 || statusCode == 410;
              return NotificationResult.builder()
                  .success(false)
                  .errorCode(String.valueOf(statusCode))
                  .errorMessage("Slack webhook error: HTTP " + statusCode + " - " + body)
                  .permanentFailure(permanent)
                  .providerResponse(body)
                  .latencyMs(latency)
                  .build();
            })
        .onErrorReturn(
            error -> {
              long latency = System.currentTimeMillis() - startTime;
              log.error("Failed to send Slack webhook notification", error);
              return NotificationResult.builder()
                  .success(false)
                  .errorMessage("HTTP request failed: " + error.getMessage())
                  .permanentFailure(false)
                  .latencyMs(latency)
                  .build();
            });
  }

}
