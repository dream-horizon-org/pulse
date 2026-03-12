package org.dreamhorizon.pulseserver.resources.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.resources.notification.models.SlackChannelListDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SlackOAuthCallbackRequest;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationChannel;
import org.dreamhorizon.pulseserver.service.notification.models.SlackChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.oauth.SlackOAuthResult;
import org.dreamhorizon.pulseserver.service.notification.oauth.SlackOAuthService;
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
class SlackOAuthControllerTest {

  Vertx vertx;

  @Mock SlackOAuthService slackOAuthService;

  SlackOAuthController controller;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    NotificationConfig config = new NotificationConfig();
    NotificationConfig.SlackOAuthConfig oauthConfig = new NotificationConfig.SlackOAuthConfig();
    oauthConfig.setUiRedirectUrl("https://app.pulse.com/settings");
    config.setSlackOAuth(oauthConfig);
    SharedDataUtils.put(vertx, config);

    controller = new SlackOAuthController(vertx, slackOAuthService);
  }

  @AfterEach
  void tearDown() {
    if (vertx != null) {
      vertx.close();
    }
  }

  @Nested
  class Callback {

    @Test
    void shouldRedirectWithErrorWhenSlackDenied() throws Exception {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setError("access_denied");

      var result = controller.callback(request).toCompletableFuture().get();

      assertThat(result.getStatus()).isEqualTo(307);
      URI location = result.getLocation();
      assertThat(location.toString()).contains("slack=error");
      assertThat(location.toString()).contains("message=access_denied");
    }

    @Test
    void shouldRedirectWithErrorWhenRequestInvalid() throws Exception {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();

      var result = controller.callback(request).toCompletableFuture().get();

      assertThat(result.getStatus()).isEqualTo(307);
      URI location = result.getLocation();
      assertThat(location.toString()).contains("slack=error");
    }

    @Test
    void shouldRedirectWithSuccessAfterOAuth() throws Exception {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setCode("auth-code");
      request.setProjectId("proj-1");

      SlackOAuthResult oauthResult = SlackOAuthResult.builder()
          .accessToken("xoxb-token")
          .workspaceName("My Workspace")
          .build();
      NotificationChannel channel = NotificationChannel.builder()
          .id(1L)
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .name("Slack - My Workspace")
          .config(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .isActive(true)
          .createdAt(Instant.now())
          .build();

      when(slackOAuthService.exchangeCodeForToken(eq("auth-code")))
          .thenReturn(Single.just(oauthResult));
      when(slackOAuthService.createOrUpdateSlackChannel(eq("proj-1"), eq(oauthResult)))
          .thenReturn(Single.just(channel));

      var result = controller.callback(request).toCompletableFuture().get();

      assertThat(result.getStatus()).isEqualTo(307);
      URI location = result.getLocation();
      assertThat(location.toString()).contains("slack=success");
      assertThat(location.toString()).contains("My+Workspace");
    }

    @Test
    void shouldRedirectWithSlackFallbackWhenWorkspaceNameNull() throws Exception {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setCode("auth-code");
      request.setProjectId("proj-1");

      SlackOAuthResult oauthResult = SlackOAuthResult.builder()
          .accessToken("xoxb-token")
          .workspaceName(null)
          .build();
      NotificationChannel channel = NotificationChannel.builder()
          .id(1L)
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .name("Slack")
          .config(SlackChannelConfig.builder().accessToken("xoxb-token").build())
          .isActive(true)
          .createdAt(Instant.now())
          .build();

      when(slackOAuthService.exchangeCodeForToken(eq("auth-code")))
          .thenReturn(Single.just(oauthResult));
      when(slackOAuthService.createOrUpdateSlackChannel(eq("proj-1"), eq(oauthResult)))
          .thenReturn(Single.just(channel));

      var result = controller.callback(request).toCompletableFuture().get();

      assertThat(result.getStatus()).isEqualTo(307);
      URI location = result.getLocation();
      assertThat(location.toString()).contains("slack=success");
      assertThat(location.toString()).contains("message=Slack");
    }

    @Test
    void shouldRedirectWithErrorOnOAuthFailure() throws Exception {
      SlackOAuthCallbackRequest request = new SlackOAuthCallbackRequest();
      request.setCode("bad-code");
      request.setProjectId("proj-1");

      when(slackOAuthService.exchangeCodeForToken(eq("bad-code")))
          .thenReturn(Single.error(new RuntimeException("Token exchange failed")));

      var result = controller.callback(request).toCompletableFuture().get();

      assertThat(result.getStatus()).isEqualTo(307);
      URI location = result.getLocation();
      assertThat(location.toString()).contains("slack=error");
      assertThat(location.toString()).contains("Token+exchange+failed");
    }
  }

  @Nested
  class Install {

    @Test
    void shouldReturnInstallUrl() throws Exception {
      when(slackOAuthService.generateInstallUrl(eq("proj-1")))
          .thenReturn(Single.just("https://slack.com/oauth/v2/authorize?client_id=123"));

      var result = controller.install("proj-1").toCompletableFuture().get();

      assertThat(result.getData()).contains("slack.com/oauth/v2/authorize");
    }
  }

  @Nested
  class ListChannels {

    @Test
    void shouldReturnChannelList() throws Exception {
      List<SlackChannelListDto> channels = List.of(
          SlackChannelListDto.builder()
              .id("C123")
              .name("general")
              .isPrivate(false)
              .isMember(true)
              .build());

      when(slackOAuthService.listWorkspaceChannels(eq("proj-1")))
          .thenReturn(Single.just(channels));

      var result = controller.listChannels("proj-1").toCompletableFuture().get();

      assertThat(result.getData()).hasSize(1);
      assertThat(result.getData().get(0).getName()).isEqualTo("general");
    }
  }
}
