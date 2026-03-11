package org.dreamhorizon.pulseserver.service.notification.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.Map;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.service.notification.TemplateService;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationMessage;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate;
import org.dreamhorizon.pulseserver.service.notification.models.SlackChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.SlackTemplateBody;
import org.dreamhorizon.pulseserver.service.notification.models.TeamsChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.TeamsTemplateBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.ses.SesClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationProvidersTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock
  WebClient webClient;

  @Mock
  HttpRequest<Buffer> httpRequest;

  @Mock
  HttpResponse<Buffer> httpResponse;

  @Mock
  TemplateService templateService;

  @Mock
  NotificationConfig notificationConfig;

  @Mock
  SesClient sesClient;

  @BeforeEach
  void setUp() {
    when(templateService.renderText(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
    when(templateService.renderJson(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Nested
  class TeamsProviderTests {

    org.dreamhorizon.pulseserver.service.notification.provider.TeamsNotificationProvider provider;

    @BeforeEach
    void setUp() {
      provider = new org.dreamhorizon.pulseserver.service.notification.provider.TeamsNotificationProvider(
          webClient, OBJECT_MAPPER, templateService);
    }

    @Test
    void shouldReturnTeamsChannelType() {
      assertThat(provider.getChannelType()).isEqualTo(ChannelType.TEAMS);
    }

    @Test
    void shouldReturnErrorWhenChannelConfigInvalid() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(null)
          .recipient("https://webhook.teams.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(TeamsTemplateBody.builder().title("Alert").text("Hello").build())
          .build();

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Invalid Teams channel configuration");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldReturnErrorWhenRecipientEmpty() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(TeamsChannelConfig.builder().workflowUrl("https://webhook.teams.com").build())
          .recipient("")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(TeamsTemplateBody.builder().title("Alert").build())
          .build();

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Teams webhook URL not provided");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldSendSuccessfullyWithPlaintTextBody() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(TeamsChannelConfig.builder().workflowUrl("https://webhook.teams.com").build())
          .recipient("https://webhook.teams.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(TeamsTemplateBody.builder().title("Alert").text("Plain text message").build())
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("ok");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getExternalId()).startsWith("teams-");
    }

    @Test
    void shouldSendSuccessfullyWithJsonBody() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(TeamsChannelConfig.builder().workflowUrl("https://webhook.teams.com").build())
          .recipient("https://webhook.teams.com")
          .params(Map.of("name", "Test"))
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(TeamsTemplateBody.builder().title("Alert").text("Hello {{name}}").build())
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("ok");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldReturnPermanentFailureOn400() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(TeamsChannelConfig.builder().workflowUrl("https://webhook.teams.com").build())
          .recipient("https://webhook.teams.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(TeamsTemplateBody.builder().text("text").build())
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(400);
      when(httpResponse.bodyAsString()).thenReturn("Bad Request");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldReturnErrorWhenRecipientNull() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(TeamsChannelConfig.builder().workflowUrl("https://webhook.teams.com").build())
          .recipient(null)
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(TeamsTemplateBody.builder().title("Alert").build())
          .build();

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Teams webhook URL not provided");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldReturnErrorOnHttpFailure() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(TeamsChannelConfig.builder().workflowUrl("https://webhook.teams.com").build())
          .recipient("https://webhook.teams.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(TeamsTemplateBody.builder().text("text").build())
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.error(new RuntimeException("Connection refused")));

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("HTTP request failed");
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldReturnNonPermanentFailureOn500() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(TeamsChannelConfig.builder().workflowUrl("https://webhook.teams.com").build())
          .recipient("https://webhook.teams.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(TeamsTemplateBody.builder().text("text").build())
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(500);
      when(httpResponse.bodyAsString()).thenReturn("Internal Server Error");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldSendWithAdaptiveCardBody() throws Exception {
      String bodyJson =
          "{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Alert\"}],\"$schema\":\"http://adaptivecards.io/schemas/adaptive-card.json\",\"version\":\"1.4\"}";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(TeamsChannelConfig.builder().workflowUrl("https://webhook.teams.com").build())
          .recipient("https://webhook.teams.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(TeamsTemplateBody.builder().body(OBJECT_MAPPER.readTree(bodyJson)).build())
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("1");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithBodyContainingTypeKey() throws Exception {
      String bodyJson = "{\"type\":\"Container\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Hello\"}]}";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(TeamsChannelConfig.builder().workflowUrl("https://webhook.teams.com").build())
          .recipient("https://webhook.teams.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(TeamsTemplateBody.builder().body(OBJECT_MAPPER.readTree(bodyJson)).build())
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("1");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithSimpleCardWithEmptyText() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(TeamsChannelConfig.builder().workflowUrl("https://webhook.teams.com").build())
          .recipient("https://webhook.teams.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(TeamsTemplateBody.builder().title("Alert only").build())
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("1");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldHandleTemplateParseFailureWithTextCardFallback() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(TeamsChannelConfig.builder().workflowUrl("https://webhook.teams.com").build())
          .recipient("https://webhook.teams.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(null)
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("1");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }
  }

  @Nested
  class SlackProviderTests {

    org.dreamhorizon.pulseserver.service.notification.provider.SlackNotificationProvider provider;

    @BeforeEach
    void setUp() {
      provider = new org.dreamhorizon.pulseserver.service.notification.provider.SlackNotificationProvider(
          webClient, OBJECT_MAPPER, templateService);
    }

    @Test
    void shouldReturnSlackChannelType() {
      assertThat(provider.getChannelType()).isEqualTo(ChannelType.SLACK);
    }

    @Test
    void shouldReturnErrorWhenChannelConfigInvalid() {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(null)
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().text("Hi").build())
          .build();

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Invalid Slack channel configuration");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldReturnErrorWhenAccessTokenMissing() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("").build())
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().text("Hi").build())
          .build();

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Slack access token not configured");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldSendSuccessfullyWithPlainText() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().text("Hello world").build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"123.456\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getExternalId()).isEqualTo("123.456");
    }

    @Test
    void shouldReturnErrorWhenSlackApiReturnsError() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().text("Hi").build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":false,\"error\":\"channel_not_found\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("channel_not_found");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldReturnErrorWhenSlackApiReturnsTransientError() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().text("Hi").build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":false,\"error\":\"ratelimited\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldReturnErrorWhenSlackApiReturnsUnknownError() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().text("Hi").build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":false}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Unknown error");
    }

    @Test
    void shouldReturnErrorWhenSlackResponseNotParseable() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().text("Hi").build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("not-json");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Failed to parse Slack response");
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldReturnSuccessWithoutTsInResponse() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().text("Hello").build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getExternalId()).isNull();
    }

    @Test
    void shouldReturnErrorOnHttpFailure() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().text("Hi").build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.error(new RuntimeException("Connection refused")));

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("HTTP request failed");
      assertThat(result.getErrorMessage()).contains("Connection refused");
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldIncludeBotNameAndIconEmojiInPayload() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder()
              .accessToken("xoxb-token")
              .botName("PulseBot")
              .iconEmoji(":robot_face:")
              .build())
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().text("Hello").build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"1.0\"}");

      var result = provider.send(message, template).blockingGet();
      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithJsonBodyContainingBlocks() throws Exception {
      String blocksJson = "[{\"type\":\"section\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"Hello\"}}]";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .params(Map.of("name", "World"))
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder()
              .text("Hi {{name}}")
              .blocks(OBJECT_MAPPER.readTree(blocksJson))
              .build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"2.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithJsonBodyAsArray() throws Exception {
      String blocksJson = "[{\"type\":\"section\",\"text\":{\"type\":\"plain_text\",\"text\":\"Alert\"}}]";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder()
              .blocks(OBJECT_MAPPER.readTree(blocksJson))
              .text("fallback")
              .build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"3.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithJsonBodyTextOnlyNoBlocks() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().text("Simple text message").build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"4.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithJsonBodyNoTextUsesRender() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder().build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"5.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldHandleTemplateParseFailureWithPlainTextFallback() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(null)
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"6.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldExtractFallbackTextFromBlocksWithObjectText() throws Exception {
      String blocksJson =
          "[{\"type\":\"section\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"Fallback from block\"}}]";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder()
              .blocks(OBJECT_MAPPER.readTree(blocksJson))
              .build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"7.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldExtractFallbackTextFromBlocksWithTextualText() throws Exception {
      String blocksJson = "[{\"type\":\"section\",\"text\":\"plain text\"}]";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body(SlackTemplateBody.builder()
              .blocks(OBJECT_MAPPER.readTree(blocksJson))
              .build())
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"8.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }
  }

  @Nested
  class EmailProviderTests {

    @Test
    void shouldReturnEmailChannelType() {
      when(notificationConfig.getRegion()).thenReturn("us-east-1");
      org.dreamhorizon.pulseserver.service.notification.provider.EmailNotificationProvider provider =
          new org.dreamhorizon.pulseserver.service.notification.provider.EmailNotificationProvider(
              templateService, notificationConfig, sesClient);

      assertThat(provider.getChannelType()).isEqualTo(ChannelType.EMAIL);
    }
  }
}
