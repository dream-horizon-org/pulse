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

    SlackChannelConfig config;
    try {
      config = objectMapper.readValue(message.getChannelConfig(), SlackChannelConfig.class);
    } catch (Exception e) {
      log.error("Failed to parse Slack channel config", e);
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
    ObjectNode payload = buildSlackPayload(config, channel, template, message);

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

  private ObjectNode buildSlackPayload(
      SlackChannelConfig config,
      String channel,
      NotificationTemplate template,
      NotificationMessage message) {

    ObjectNode payload = objectMapper.createObjectNode();
    payload.put(Slack.KEY_CHANNEL, channel);

    String bodyJson = template.getBody();

    if (isJson(bodyJson)) {
      try {
        JsonNode body = objectMapper.readTree(bodyJson);

        String text =
            body.has(KEY_TEXT)
                ? templateService.renderText(body.get(KEY_TEXT).asText(), message.getParams())
                : null;

        if (body.has(KEY_BLOCKS)) {
          String blocksRendered =
              templateService.renderJson(
                  objectMapper.writeValueAsString(body.get(KEY_BLOCKS)), message.getParams());
          JsonNode blocks = objectMapper.readTree(blocksRendered);
          payload.set(KEY_BLOCKS, blocks);
          payload.put(KEY_TEXT, text != null ? text : extractFallbackText(blocks));
        } else if (body.isArray()) {
          String blocksRendered = templateService.renderJson(bodyJson, message.getParams());
          JsonNode blocks = objectMapper.readTree(blocksRendered);
          payload.set(KEY_BLOCKS, blocks);
          payload.put(KEY_TEXT, extractFallbackText(blocks));
        } else if (text != null) {
          payload.put(KEY_TEXT, text);
        } else {
          payload.put(KEY_TEXT, templateService.renderText(bodyJson, message.getParams()));
        }
      } catch (Exception e) {
        log.warn("Failed to parse Slack template body, using as plain text", e);
        payload.put(KEY_TEXT, templateService.renderText(bodyJson, message.getParams()));
      }
    } else {
      payload.put(KEY_TEXT, templateService.renderText(bodyJson, message.getParams()));
    }

    if (config.getBotName() != null) {
      payload.put(Slack.KEY_USERNAME, config.getBotName());
    }
    if (config.getIconEmoji() != null) {
      payload.put(Slack.KEY_ICON_EMOJI, config.getIconEmoji());
    }

    return payload;
  }

  private boolean isJson(String content) {
    if (content == null || content.isEmpty()) {
      return false;
    }
    String trimmed = content.trim();
    return (trimmed.startsWith("{") && trimmed.endsWith("}"))
        || (trimmed.startsWith("[") && trimmed.endsWith("]"));
  }

  private String extractFallbackText(JsonNode blocks) {
    if (blocks == null || !blocks.isArray()) {
      return DEFAULT_SUBJECT;
    }

    StringBuilder text = new StringBuilder();
    for (JsonNode block : blocks) {
      if (block.has(KEY_TEXT)) {
        JsonNode textNode = block.get(KEY_TEXT);
        if (textNode.isObject() && textNode.has(KEY_TEXT)) {
          text.append(textNode.get(KEY_TEXT).asText()).append(" ");
        } else if (textNode.isTextual()) {
          text.append(textNode.asText()).append(" ");
        }
      }
    }

    return !text.isEmpty() ? text.toString().trim() : DEFAULT_SUBJECT;
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
