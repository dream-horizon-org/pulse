package org.dreamhorizon.pulseserver.service.notification.oauth;

import static org.dreamhorizon.pulseserver.constant.NotificationConstants.Slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.config.NotificationConfig.SlackOAuthConfig;
import org.dreamhorizon.pulseserver.dao.notification.NotificationChannelDao;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.notification.models.SlackChannelListDto;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationChannel;
import org.dreamhorizon.pulseserver.service.notification.models.SlackChannelConfig;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;

@Slf4j
@Singleton
public class SlackOAuthService {

  private final NotificationChannelDao channelDao;
  private final WebClient webClient;
  private final ObjectMapper objectMapper;
  private final SlackOAuthConfig config;

  @Inject
  public SlackOAuthService(
      Vertx vertx,
      NotificationChannelDao channelDao,
      WebClient webClient,
      ObjectMapper objectMapper) {
    this.channelDao = channelDao;
    this.webClient = webClient;
    this.objectMapper = objectMapper;
    this.config = SharedDataUtils.get(vertx, NotificationConfig.class).getSlackOAuthConfig();
  }

  public Single<String> generateInstallUrl(String projectId) {
    if (!config.isEnabled()) {
      throw ServiceError.INVALID_REQUEST_BODY.getCustomException(
          "Slack OAuth is not configured. Set SLACK_CLIENT_ID and SLACK_CLIENT_SECRET.");
    }

    StringBuilder url = new StringBuilder(Slack.OAUTH_AUTHORIZE_URL);
    url.append("?client_id=").append(encode(config.getClientId()));
    url.append("&scope=").append(encode(config.getScopes()));
    url.append("&redirect_uri=").append(encode(config.getRedirectUri()));
    url.append("&state=").append(encode(projectId));

    return Single.just(url.toString());
  }

  public Single<SlackOAuthResult> exchangeCodeForToken(String code) {
    if (!config.isEnabled()) {
      return Single.error(
          ServiceError.INVALID_REQUEST_BODY.getCustomException(
              "Slack OAuth is not configured"));
    }

    String formBody = String.format(
        "client_id=%s&client_secret=%s&code=%s&redirect_uri=%s",
        encode(config.getClientId()),
        encode(config.getClientSecret()),
        encode(code),
        encode(config.getRedirectUri()));

    return webClient
        .postAbs(Slack.OAUTH_ACCESS_URL)
        .putHeader("Content-Type", "application/x-www-form-urlencoded")
        .rxSendBuffer(io.vertx.rxjava3.core.buffer.Buffer.buffer(formBody))
        .flatMap(response -> {
          try {
            JsonNode json = objectMapper.readTree(response.bodyAsString());

            if (!json.has("ok") || !json.get("ok").asBoolean()) {
              String error = json.has("error") ? json.get("error").asText() : "Unknown error";
              log.error("Slack OAuth error: {}", error);
              return Single.error(
                  ServiceError.INVALID_SLACK_CODE.getCustomException("Slack OAuth error: " + error));
            }

            String accessToken = json.get("access_token").asText();
            String workspaceId = json.has("team") ? json.get("team").get("id").asText() : null;
            String workspaceName = json.has("team") ? json.get("team").get("name").asText() : null;
            String botUserId = json.has("bot_user_id") ? json.get("bot_user_id").asText() : null;

            return Single.just(SlackOAuthResult.builder()
                .accessToken(accessToken)
                .workspaceId(workspaceId)
                .workspaceName(workspaceName)
                .botUserId(botUserId)
                .build());
          } catch (Exception e) {
            log.error("Failed to parse Slack OAuth response", e);
            return Single.error(
                ServiceError.INTERNAL_SERVER_ERROR.getCustomException(
                    "Failed to parse Slack OAuth response"));
          }
        });
  }

  public Single<NotificationChannel> createOrUpdateSlackChannel(
      String projectId, SlackOAuthResult oauthResult) {

    SlackChannelConfig channelConfig = SlackChannelConfig.builder()
        .accessToken(oauthResult.getAccessToken())
        .workspaceId(oauthResult.getWorkspaceId())
        .botName("Pulse")
        .build();

    String channelName = oauthResult.getWorkspaceName() != null
        ? "Slack - " + oauthResult.getWorkspaceName()
        : "Slack";

    return channelDao
        .getActiveChannelByType(projectId, ChannelType.SLACK)
        .flatMapSingle(existingChannel -> {
          NotificationChannel updated = NotificationChannel.builder()
              .name(channelName)
              .config(channelConfig)
              .isActive(true)
              .build();
          return channelDao
              .updateChannel(existingChannel.getId(), updated)
              .flatMap(count -> channelDao.getChannelById(existingChannel.getId()).toSingle());
        })
        .switchIfEmpty(Single.defer(() -> {
          NotificationChannel newChannel = NotificationChannel.builder()
              .projectId(projectId)
              .channelType(ChannelType.SLACK)
              .name(channelName)
              .config(channelConfig)
              .isActive(true)
              .build();
          return channelDao
              .createChannel(newChannel)
              .flatMap(id -> channelDao.getChannelById(id).toSingle());
        }));
  }

  public Single<List<SlackChannelListDto>> listWorkspaceChannels(String projectId) {
    return channelDao
        .getActiveChannelByType(projectId, ChannelType.SLACK)
        .switchIfEmpty(
            Maybe.error(
                ServiceError.NOT_FOUND.getCustomException(
                    "No active Slack channel found for project")))
        .toSingle()
        .flatMap(
            channel -> {
              if (!(channel.getConfig() instanceof SlackChannelConfig slackConfig)) {
                return Single.error(
                    ServiceError.INTERNAL_SERVER_ERROR.getCustomException(
                        "Expected SlackChannelConfig but got: " + channel.getConfig().getClass().getSimpleName()));
              }

              if (slackConfig.getAccessToken() == null) {
                return Single.error(
                    ServiceError.INTERNAL_SERVER_ERROR.getCustomException(
                        "Slack access token not configured"));
              }

              return webClient
                  .getAbs("https://slack.com/api/conversations.list")
                  .putHeader(
                      "Authorization", "Bearer " + slackConfig.getAccessToken())
                  .addQueryParam("types", "public_channel")
                  .addQueryParam("exclude_archived", "true")
                  .addQueryParam("limit", "200")
                  .rxSend()
                  .map(
                      response -> {
                        try {
                          JsonNode json = objectMapper.readTree(response.bodyAsString());
                          if (!json.has("ok") || !json.get("ok").asBoolean()) {
                            return Collections.<SlackChannelListDto>emptyList();
                          }
                          List<SlackChannelListDto> channels = new ArrayList<>();
                          JsonNode channelsNode = json.get("channels");
                          if (channelsNode != null && channelsNode.isArray()) {
                            for (JsonNode ch : channelsNode) {
                              channels.add(
                                  SlackChannelListDto.builder()
                                      .id(ch.get("id").asText())
                                      .name(ch.get("name").asText())
                                      .isPrivate(
                                          ch.has("is_private")
                                              && ch.get("is_private").asBoolean())
                                      .isMember(
                                          ch.has("is_member")
                                              && ch.get("is_member").asBoolean())
                                      .build());
                            }
                          }
                          return channels;
                        } catch (Exception e) {
                          log.error("Failed to parse Slack channels response", e);
                          return Collections.<SlackChannelListDto>emptyList();
                        }
                      });
            });
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
