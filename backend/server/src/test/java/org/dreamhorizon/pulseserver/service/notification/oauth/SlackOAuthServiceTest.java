package org.dreamhorizon.pulseserver.service.notification.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.time.Instant;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.dao.notification.NotificationChannelDao;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationChannel;
import org.dreamhorizon.pulseserver.service.notification.models.SlackChannelConfig;
import org.dreamhorizon.pulseserver.vertx.SharedDataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlackOAuthServiceTest {

  Vertx vertx;
  ObjectMapper objectMapper = new ObjectMapper();

  @Mock
  NotificationChannelDao channelDao;

  @Mock
  WebClient webClient;

  @Mock
  io.vertx.rxjava3.ext.web.client.HttpRequest<io.vertx.rxjava3.core.buffer.Buffer> httpRequest;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
  }

  @AfterEach
  void tearDown() {
    if (vertx != null) {
      vertx.close();
    }
  }

  @Nested
  class GenerateInstallUrl {

    @Test
    void shouldThrowWhenOAuthDisabled() {
      NotificationConfig config = new NotificationConfig();
      config.setSlackOAuth(new NotificationConfig.SlackOAuthConfig());
      SharedDataUtils.put(vertx, config);

      SlackOAuthService service = new SlackOAuthService(vertx, channelDao, webClient, objectMapper);

      assertThatThrownBy(() -> service.generateInstallUrl("proj-1").blockingGet())
          .hasMessageContaining("Slack OAuth is not configured");
    }

    @Test
    void shouldReturnInstallUrlWhenEnabled() {
      NotificationConfig config = new NotificationConfig();
      NotificationConfig.SlackOAuthConfig oauthConfig = new NotificationConfig.SlackOAuthConfig();
      oauthConfig.setClientId("client-123");
      oauthConfig.setClientSecret("secret-456");
      oauthConfig.setRedirectUri("https://app.com/slack/callback");
      oauthConfig.setScopes("chat:write,channels:read");
      config.setSlackOAuth(oauthConfig);
      SharedDataUtils.put(vertx, config);

      SlackOAuthService service = new SlackOAuthService(vertx, channelDao, webClient, objectMapper);

      String url = service.generateInstallUrl("proj-1").blockingGet();

      assertThat(url).contains("slack.com/oauth/v2/authorize");
      assertThat(url).contains("client_id=client-123");
      assertThat(url).contains("scope=chat%3Awrite%2Cchannels%3Aread");
      assertThat(url).contains("redirect_uri=https%3A%2F%2Fapp.com%2Fslack%2Fcallback");
      assertThat(url).contains("state=proj-1");
    }
  }

  @Nested
  class ExchangeCodeForToken {

    @Test
    void shouldReturnErrorWhenOAuthDisabled() {
      NotificationConfig config = new NotificationConfig();
      config.setSlackOAuth(new NotificationConfig.SlackOAuthConfig());
      SharedDataUtils.put(vertx, config);

      SlackOAuthService service = new SlackOAuthService(vertx, channelDao, webClient, objectMapper);

      assertThatThrownBy(() -> service.exchangeCodeForToken("code-123").blockingGet())
          .hasMessageContaining("Slack OAuth is not configured");
    }

    @Test
    void shouldReturnTokenWhenSlackRespondsSuccessfully() {
      NotificationConfig config = new NotificationConfig();
      NotificationConfig.SlackOAuthConfig oauthConfig = new NotificationConfig.SlackOAuthConfig();
      oauthConfig.setClientId("client-123");
      oauthConfig.setClientSecret("secret-456");
      oauthConfig.setRedirectUri("https://app.com/callback");
      config.setSlackOAuth(oauthConfig);
      SharedDataUtils.put(vertx, config);

      String slackResponse = "{\"ok\":true,\"access_token\":\"xoxb-123\",\"team\":{\"id\":\"T123\",\"name\":\"Workspace\"},\"bot_user_id\":\"U123\"}";
      HttpResponse<io.vertx.rxjava3.core.buffer.Buffer> mockResponse = org.mockito.Mockito.mock(HttpResponse.class);
      when(mockResponse.bodyAsString()).thenReturn(slackResponse);
      when(webClient.postAbs(any(String.class))).thenReturn(httpRequest);
      when(httpRequest.putHeader(any(String.class), any(String.class))).thenReturn(httpRequest);
      when(httpRequest.rxSendBuffer(any())).thenReturn(Single.just(mockResponse));

      SlackOAuthService service = new SlackOAuthService(vertx, channelDao, webClient, objectMapper);

      SlackOAuthResult result = service.exchangeCodeForToken("code-123").blockingGet();

      assertThat(result.getAccessToken()).isEqualTo("xoxb-123");
      assertThat(result.getWorkspaceId()).isEqualTo("T123");
      assertThat(result.getWorkspaceName()).isEqualTo("Workspace");
      assertThat(result.getBotUserId()).isEqualTo("U123");
    }

    @Test
    void shouldReturnErrorWhenSlackRespondsWithOkFalse() {
      NotificationConfig config = new NotificationConfig();
      NotificationConfig.SlackOAuthConfig oauthConfig = new NotificationConfig.SlackOAuthConfig();
      oauthConfig.setClientId("client-123");
      oauthConfig.setClientSecret("secret-456");
      oauthConfig.setRedirectUri("https://app.com/callback");
      config.setSlackOAuth(oauthConfig);
      SharedDataUtils.put(vertx, config);

      String slackResponse = "{\"ok\":false,\"error\":\"invalid_code\"}";
      HttpResponse<io.vertx.rxjava3.core.buffer.Buffer> mockResponse = org.mockito.Mockito.mock(HttpResponse.class);
      when(mockResponse.bodyAsString()).thenReturn(slackResponse);
      when(webClient.postAbs(any(String.class))).thenReturn(httpRequest);
      when(httpRequest.putHeader(any(String.class), any(String.class))).thenReturn(httpRequest);
      when(httpRequest.rxSendBuffer(any())).thenReturn(Single.just(mockResponse));

      SlackOAuthService service = new SlackOAuthService(vertx, channelDao, webClient, objectMapper);

      assertThatThrownBy(() -> service.exchangeCodeForToken("bad-code").blockingGet())
          .hasMessageContaining("Slack OAuth error");
    }

    @Test
    void shouldReturnTokenWithNullWorkspaceWhenNoTeamInResponse() {
      NotificationConfig config = new NotificationConfig();
      NotificationConfig.SlackOAuthConfig oauthConfig = new NotificationConfig.SlackOAuthConfig();
      oauthConfig.setClientId("client-123");
      oauthConfig.setClientSecret("secret-456");
      oauthConfig.setRedirectUri("https://app.com/callback");
      config.setSlackOAuth(oauthConfig);
      SharedDataUtils.put(vertx, config);

      String slackResponse = "{\"ok\":true,\"access_token\":\"xoxb-456\"}";
      HttpResponse<io.vertx.rxjava3.core.buffer.Buffer> mockResponse = org.mockito.Mockito.mock(HttpResponse.class);
      when(mockResponse.bodyAsString()).thenReturn(slackResponse);
      when(webClient.postAbs(any(String.class))).thenReturn(httpRequest);
      when(httpRequest.putHeader(any(String.class), any(String.class))).thenReturn(httpRequest);
      when(httpRequest.rxSendBuffer(any())).thenReturn(Single.just(mockResponse));

      SlackOAuthService service = new SlackOAuthService(vertx, channelDao, webClient, objectMapper);

      SlackOAuthResult result = service.exchangeCodeForToken("code").blockingGet();

      assertThat(result.getAccessToken()).isEqualTo("xoxb-456");
      assertThat(result.getWorkspaceId()).isNull();
      assertThat(result.getWorkspaceName()).isNull();
    }
  }

  @Nested
  class CreateOrUpdateSlackChannel {

    @Test
    void shouldUpdateExistingChannel() {
      NotificationConfig config = new NotificationConfig();
      config.setSlackOAuth(new NotificationConfig.SlackOAuthConfig());
      SharedDataUtils.put(vertx, config);

      SlackOAuthResult oauthResult = SlackOAuthResult.builder()
          .accessToken("xoxb-123")
          .workspaceName("My Workspace")
          .build();

      SlackChannelConfig oldConfig = SlackChannelConfig.builder().accessToken("old-token").build();
      SlackChannelConfig newConfig = SlackChannelConfig.builder()
          .accessToken("xoxb-123")
          .botName("Pulse")
          .build();

      NotificationChannel existingChannel = NotificationChannel.builder()
          .id(10L)
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .name("Slack - Old")
          .config(oldConfig)
          .isActive(true)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      NotificationChannel updatedChannel = NotificationChannel.builder()
          .id(10L)
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .name("Slack - My Workspace")
          .config(newConfig)
          .isActive(true)
          .build();

      when(channelDao.getActiveChannelByType(eq("proj-1"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(existingChannel));
      when(channelDao.updateChannel(eq(10L), any(NotificationChannel.class)))
          .thenReturn(Single.just(1));
      when(channelDao.getChannelById(eq(10L)))
          .thenReturn(Maybe.just(updatedChannel));

      SlackOAuthService service = new SlackOAuthService(vertx, channelDao, webClient, objectMapper);

      NotificationChannel result = service.createOrUpdateSlackChannel("proj-1", oauthResult).blockingGet();

      assertThat(result.getName()).isEqualTo("Slack - My Workspace");
      verify(channelDao).updateChannel(eq(10L), any(NotificationChannel.class));
    }

    @Test
    void shouldCreateNewChannelWhenNoneExists() {
      NotificationConfig config = new NotificationConfig();
      config.setSlackOAuth(new NotificationConfig.SlackOAuthConfig());
      SharedDataUtils.put(vertx, config);

      SlackOAuthResult oauthResult = SlackOAuthResult.builder()
          .accessToken("xoxb-789")
          .workspaceName("New Workspace")
          .build();

      when(channelDao.getActiveChannelByType(eq("proj-2"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.empty());
      when(channelDao.createChannel(any(NotificationChannel.class)))
          .thenReturn(Single.just(20L));
      SlackChannelConfig createdConfig = SlackChannelConfig.builder()
          .accessToken("xoxb-789")
          .botName("Pulse")
          .build();
      when(channelDao.getChannelById(eq(20L)))
          .thenReturn(Maybe.just(NotificationChannel.builder()
              .id(20L)
              .projectId("proj-2")
              .channelType(ChannelType.SLACK)
              .name("Slack - New Workspace")
              .config(createdConfig)
              .isActive(true)
              .build()));

      SlackOAuthService service = new SlackOAuthService(vertx, channelDao, webClient, objectMapper);

      NotificationChannel result = service.createOrUpdateSlackChannel("proj-2", oauthResult).blockingGet();

      assertThat(result.getName()).isEqualTo("Slack - New Workspace");
      verify(channelDao).createChannel(any(NotificationChannel.class));
    }

    @Test
    void shouldUseSlackAsNameWhenWorkspaceNameIsNull() {
      NotificationConfig config = new NotificationConfig();
      config.setSlackOAuth(new NotificationConfig.SlackOAuthConfig());
      SharedDataUtils.put(vertx, config);

      SlackOAuthResult oauthResult = SlackOAuthResult.builder()
          .accessToken("xoxb-abc")
          .workspaceName(null)
          .build();

      when(channelDao.getActiveChannelByType(eq("proj-3"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.empty());
      when(channelDao.createChannel(any(NotificationChannel.class)))
          .thenReturn(Single.just(30L));
      when(channelDao.getChannelById(eq(30L)))
          .thenReturn(Maybe.just(NotificationChannel.builder()
              .id(30L)
              .projectId("proj-3")
              .channelType(ChannelType.SLACK)
              .name("Slack")
              .config(SlackChannelConfig.builder().accessToken("xoxb-abc").build())
              .isActive(true)
              .build()));

      SlackOAuthService service = new SlackOAuthService(vertx, channelDao, webClient, objectMapper);

      NotificationChannel result = service.createOrUpdateSlackChannel("proj-3", oauthResult).blockingGet();

      assertThat(result.getName()).isEqualTo("Slack");
      verify(channelDao).createChannel(org.mockito.ArgumentMatchers.argThat(ch ->
          "Slack".equals(ch.getName())));
    }
  }
}
