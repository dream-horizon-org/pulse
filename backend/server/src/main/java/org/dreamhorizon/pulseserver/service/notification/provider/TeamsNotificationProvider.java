package org.dreamhorizon.pulseserver.service.notification.provider;

import static org.dreamhorizon.pulseserver.constant.NotificationConstants.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.notification.TemplateService;
import org.dreamhorizon.pulseserver.service.notification.models.*;

@Slf4j
@Singleton
public class TeamsNotificationProvider implements NotificationProvider {

  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final TemplateService templateService;

  @Inject
  public TeamsNotificationProvider(
      WebClient webClient, ObjectMapper objectMapper, TemplateService templateService) {
    this.webClient = webClient;
    this.objectMapper = objectMapper;
    this.templateService = templateService;
    log.info("Teams notification provider initialized");
  }

  @Override
  public ChannelType getChannelType() {
    return ChannelType.TEAMS;
  }

  @Override
  public Single<NotificationResult> send(
      NotificationMessage message, NotificationTemplate template) {
    long startTime = System.currentTimeMillis();

    if (!(message.getChannelConfig() instanceof TeamsChannelConfig)) {
      log.error("Expected TeamsChannelConfig but got: {}",
          message.getChannelConfig() != null ? message.getChannelConfig().getClass().getSimpleName() : "null");
      return Single.just(
          NotificationResult.builder()
              .success(false)
              .errorMessage("Invalid Teams channel configuration")
              .permanentFailure(true)
              .build());
    }

    String webhookUrl = message.getRecipient();
    if (webhookUrl == null || webhookUrl.isEmpty()) {
      return Single.just(
          NotificationResult.builder()
              .success(false)
              .errorMessage("Teams webhook URL not provided")
              .permanentFailure(true)
              .build());
    }

    ObjectNode payload = buildTeamsPayload(template, message);

    JsonObject jsonPayload;
    try {
      jsonPayload = new JsonObject(objectMapper.writeValueAsString(payload));
    } catch (Exception e) {
      log.error("Failed to serialize Teams payload", e);
      return Single.just(
          NotificationResult.builder()
              .success(false)
              .errorMessage("Failed to serialize Teams payload")
              .permanentFailure(true)
              .build());
    }

    return webClient
        .postAbs(webhookUrl)
        .putHeader("Content-Type", "application/json")
        .timeout(HTTP_REQUEST_TIMEOUT_SECONDS * 1000L)
        .rxSendJsonObject(jsonPayload)
        .map(
            response ->
                parseTeamsResponse(response.statusCode(), response.bodyAsString(), startTime))
        .onErrorReturn(
            error -> {
              long latency = System.currentTimeMillis() - startTime;
              log.error("Failed to send Teams notification to {}", webhookUrl, error);
              return NotificationResult.builder()
                  .success(false)
                  .errorMessage("HTTP request failed: " + error.getMessage())
                  .permanentFailure(false)
                  .latencyMs(latency)
                  .build();
            });
  }

  private ObjectNode buildTeamsPayload(NotificationTemplate template, NotificationMessage message) {
    if (!(template.getBody() instanceof TeamsTemplateBody teamsBody)) {
      return createTextCard(DEFAULT_SUBJECT);
    }

    boolean hasAdaptiveCardContent = teamsBody.getBody() != null;

    if (hasAdaptiveCardContent) {
      try {
        ObjectNode cardContent = objectMapper.createObjectNode();

        String renderedBody = templateService.renderJson(
            objectMapper.writeValueAsString(teamsBody.getBody()), message.getParams());
        JsonNode bodyElements = objectMapper.readTree(renderedBody);
        cardContent.set(KEY_BODY, bodyElements.isArray() ? bodyElements : objectMapper.createArrayNode().add(bodyElements));

        if (teamsBody.getActions() != null) {
          String renderedActions = templateService.renderJson(
              objectMapper.writeValueAsString(teamsBody.getActions()), message.getParams());
          cardContent.set(KEY_ACTIONS, objectMapper.readTree(renderedActions));
        }

        return wrapInAdaptiveCard(cardContent);
      } catch (Exception e) {
        log.warn("Failed to render Teams adaptive card, falling back to simple card", e);
      }
    }

    String title = teamsBody.getTitle() != null
        ? templateService.renderText(teamsBody.getTitle(), message.getParams())
        : DEFAULT_SUBJECT;
    String text = teamsBody.getText() != null
        ? templateService.renderText(teamsBody.getText(), message.getParams())
        : "";

    return createSimpleCard(title, text);
  }

  private ObjectNode wrapInAdaptiveCard(JsonNode content) {

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.put(KEY_TYPE, Teams.TYPE_MESSAGE);

    ArrayNode attachments = objectMapper.createArrayNode();
    ObjectNode attachment = objectMapper.createObjectNode();
    attachment.put(Teams.KEY_CONTENT_TYPE, Teams.CONTENT_TYPE_ADAPTIVE_CARD);

    ObjectNode card = objectMapper.createObjectNode();
    card.put(KEY_TYPE, Teams.TYPE_ADAPTIVE_CARD);
    card.put(Teams.KEY_SCHEMA, Teams.ADAPTIVE_CARD_SCHEMA);
    card.put(Teams.KEY_VERSION, Teams.ADAPTIVE_CARD_VERSION);

    if (content.has(KEY_BODY)) {
      card.set(KEY_BODY, content.get(KEY_BODY));
    }
    if (content.has(KEY_ACTIONS)) {
      card.set(KEY_ACTIONS, content.get(KEY_ACTIONS));
    }

    attachment.set(Teams.KEY_CONTENT, card);
    attachments.add(attachment);
    wrapper.set(Teams.KEY_ATTACHMENTS, attachments);

    return wrapper;
  }

  private ObjectNode createSimpleCard(String title, String text) {
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.put(KEY_TYPE, Teams.TYPE_MESSAGE);

    ArrayNode attachments = objectMapper.createArrayNode();
    ObjectNode attachment = objectMapper.createObjectNode();
    attachment.put(Teams.KEY_CONTENT_TYPE, Teams.CONTENT_TYPE_ADAPTIVE_CARD);

    ObjectNode card = objectMapper.createObjectNode();
    card.put(KEY_TYPE, Teams.TYPE_ADAPTIVE_CARD);
    card.put(Teams.KEY_SCHEMA, Teams.ADAPTIVE_CARD_SCHEMA);
    card.put(Teams.KEY_VERSION, Teams.ADAPTIVE_CARD_VERSION);

    ArrayNode body = objectMapper.createArrayNode();

    ObjectNode titleBlock = objectMapper.createObjectNode();
    titleBlock.put(KEY_TYPE, Teams.TYPE_TEXT_BLOCK);
    titleBlock.put(KEY_TEXT, title);
    titleBlock.put(Teams.KEY_WEIGHT, Teams.WEIGHT_BOLDER);
    titleBlock.put(Teams.KEY_SIZE, Teams.SIZE_MEDIUM);
    titleBlock.put(Teams.KEY_WRAP, true);
    body.add(titleBlock);

    if (text != null && !text.isEmpty()) {
      ObjectNode textBlock = objectMapper.createObjectNode();
      textBlock.put(KEY_TYPE, Teams.TYPE_TEXT_BLOCK);
      textBlock.put(KEY_TEXT, text);
      textBlock.put(Teams.KEY_WRAP, true);
      body.add(textBlock);
    }

    card.set(KEY_BODY, body);
    attachment.set(Teams.KEY_CONTENT, card);
    attachments.add(attachment);
    wrapper.set(Teams.KEY_ATTACHMENTS, attachments);

    return wrapper;
  }

  private ObjectNode createTextCard(String text) {
    return createSimpleCard(DEFAULT_SUBJECT, text);
  }

  private NotificationResult parseTeamsResponse(
      int statusCode, String responseBody, long startTime) {

    long latency = System.currentTimeMillis() - startTime;

    if (statusCode >= 200 && statusCode < 300) {
      return NotificationResult.builder()
          .success(true)
          .externalId(generateExternalId())
          .providerResponse(responseBody)
          .latencyMs(latency)
          .build();
    } else {
      boolean permanent = isPermanentError(statusCode);

      return NotificationResult.builder()
          .success(false)
          .errorCode(String.valueOf(statusCode))
          .errorMessage("Teams webhook error: HTTP " + statusCode)
          .permanentFailure(permanent)
          .providerResponse(responseBody)
          .latencyMs(latency)
          .build();
    }
  }

  private String generateExternalId() {
    return "teams-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private boolean isPermanentError(int statusCode) {
    return Teams.PERMANENT_ERROR_STATUS_CODES.contains(statusCode);
  }
}
