package org.dreamhorizon.pulseserver.service.notification.provider;

import static org.dreamhorizon.pulseserver.constant.NotificationConstants.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.notification.TemplateService;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationMessage;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate;
import org.dreamhorizon.pulseserver.service.notification.models.SlackTemplateBody;

@Slf4j
public final class SlackPayloadBuilder {

  private SlackPayloadBuilder() {}

  public static ObjectNode buildPayload(
      ObjectMapper objectMapper,
      TemplateService templateService,
      NotificationTemplate template,
      NotificationMessage message,
      String botName,
      String iconEmoji) {

    ObjectNode payload = objectMapper.createObjectNode();

    if (template.getBody() instanceof SlackTemplateBody slackBody) {
      String renderedText = null;
      JsonNode renderedBlocks = null;

      if (slackBody.getBlocks() != null) {
        try {
          String blocksRendered = templateService.renderJson(
              objectMapper.writeValueAsString(slackBody.getBlocks()), message.getParams());
          renderedBlocks = objectMapper.readTree(blocksRendered);
          renderedText = slackBody.getText() != null
              ? templateService.renderText(slackBody.getText(), message.getParams())
              : extractFallbackText(renderedBlocks);
        } catch (Exception e) {
          log.warn("Failed to render Slack blocks, falling back to text", e);
          renderedText = slackBody.getText() != null
              ? templateService.renderText(slackBody.getText(), message.getParams())
              : DEFAULT_SUBJECT;
        }
      } else if (slackBody.getText() != null) {
        renderedText = templateService.renderText(slackBody.getText(), message.getParams());
      } else {
        renderedText = DEFAULT_SUBJECT;
      }

      if (slackBody.getColor() != null && !slackBody.getColor().isBlank()) {
        ObjectNode attachment = objectMapper.createObjectNode();
        attachment.put(Slack.KEY_COLOR, slackBody.getColor());
        if (renderedBlocks != null) {
          attachment.set(KEY_BLOCKS, renderedBlocks);
        }
        if (renderedText != null) {
          attachment.put(KEY_TEXT, renderedText);
          attachment.put("fallback", renderedText);
          ArrayNode mrkdwnIn = objectMapper.createArrayNode();
          mrkdwnIn.add(KEY_TEXT);
          attachment.set(Slack.KEY_MRKDWN_IN, mrkdwnIn);
        }
        ArrayNode attachments = objectMapper.createArrayNode();
        attachments.add(attachment);
        payload.set(Slack.KEY_ATTACHMENTS, attachments);
      } else {
        if (renderedBlocks != null) {
          payload.set(KEY_BLOCKS, renderedBlocks);
        }
        if (renderedText != null) {
          payload.put(KEY_TEXT, renderedText);
        }
      }
    } else {
      payload.put(KEY_TEXT, DEFAULT_SUBJECT);
    }

    if (botName != null) {
      payload.put(Slack.KEY_USERNAME, botName);
    }
    if (iconEmoji != null) {
      payload.put(Slack.KEY_ICON_EMOJI, iconEmoji);
    }

    return payload;
  }

  static String extractFallbackText(JsonNode blocks) {
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
}
