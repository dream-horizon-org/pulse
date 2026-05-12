package org.dreamhorizon.pulseserver.service.alerts.grafana.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest.GrafanaAlert;
import org.dreamhorizon.pulseserver.service.alerts.grafana.SlackPoster;
import org.dreamhorizon.pulseserver.service.oncall.OnCallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Service-level tests for the Grafana alert handler.
 *
 * <p>{@link OnCallService} is concrete and can't be mocked by Mockito 4.x on Java 23 without a
 * dynamic-agent flag, so we use a hand-rolled subclass that returns whatever {@code Single} the
 * test needs. {@link SlackPoster} is an interface and works fine with regular mocks.
 */
class GrafanaAlertServiceImplTest {

  private static final String ALERTS_CHANNEL = "C123ALERT";
  private static final String FALLBACK_CHANNEL = "C999FALL";
  private static final String BOT_TOKEN = "xoxb-test-token";
  private static final String SLACK_MENTION = "<@U07YASIR>";

  private AtomicReference<Single<String>> mentionReturn;
  private SlackPoster slackPoster;
  private NotificationConfig notificationConfig;
  private GrafanaAlertServiceImpl service;

  @BeforeEach
  void setUp() {
    mentionReturn = new AtomicReference<>(Single.just(SLACK_MENTION));

    NotificationConfig.GoAlertConfig goAlert = new NotificationConfig.GoAlertConfig();
    goAlert.setSlackBotToken(BOT_TOKEN);

    NotificationConfig.IncidentConfig incident = new NotificationConfig.IncidentConfig();
    incident.setGoAlert(goAlert);

    NotificationConfig.GrafanaAlertsConfig grafanaCfg = new NotificationConfig.GrafanaAlertsConfig();
    grafanaCfg.setSlackChannelId(ALERTS_CHANNEL);
    grafanaCfg.setFallbackSlackChannelId(FALLBACK_CHANNEL);
    NotificationConfig.AlertsConfig alerts = new NotificationConfig.AlertsConfig();
    alerts.setGrafana(grafanaCfg);

    notificationConfig = new NotificationConfig();
    notificationConfig.setIncident(incident);
    notificationConfig.setAlerts(alerts);

    slackPoster = mock(SlackPoster.class);
    when(slackPoster.postMessage(anyString(), anyString(), anyString()))
        .thenReturn(Single.just(new JsonObject().put("ok", true)));

    OnCallService onCallStub = new OnCallService(null, null, notificationConfig) {
      @Override
      public Single<String> getOnCallSlackMentions() {
        return mentionReturn.get();
      }
    };

    service = new GrafanaAlertServiceImpl(onCallStub, slackPoster, notificationConfig);
  }

  private GrafanaWebhookRequest buildRequest(String status, String alertName, String summary) {
    return GrafanaWebhookRequest.builder()
        .status(status)
        .alerts(List.of(GrafanaAlert.builder()
            .status(status)
            .labels(Map.of("alertname", alertName, "severity", "warning"))
            .annotations(Map.of("summary", summary))
            .generatorURL("http://grafana.example.com/alerts/1")
            .build()))
        .build();
  }

  // ---------- happy path ----------

  @Test
  void shouldPostFiringMessageWithOnCallMention() {
    service.handleAlert(buildRequest("firing", "CpuHigh", "CPU > 80%")).blockingAwait();

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(slackPoster).postMessage(eq(ALERTS_CHANNEL), eq(BOT_TOKEN), textCaptor.capture());

    String text = textCaptor.getValue();
    assertThat(text).contains(SLACK_MENTION);
    assertThat(text).contains(":rotating_light:");
    assertThat(text).contains("FIRING");
    assertThat(text).contains("CpuHigh");
    assertThat(text).contains("CPU > 80%");
    assertThat(text).contains("View in Grafana");
  }

  @Test
  void shouldPostResolvedMessageWithCheckmarkEmoji() {
    service.handleAlert(buildRequest("resolved", "CpuHigh", "back to normal")).blockingAwait();

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(slackPoster).postMessage(anyString(), anyString(), textCaptor.capture());

    assertThat(textCaptor.getValue()).contains(":white_check_mark:");
    assertThat(textCaptor.getValue()).contains("RESOLVED");
  }

  @Test
  void shouldPassBotTokenToSlackPoster() {
    service.handleAlert(buildRequest("firing", "X", "y")).blockingAwait();

    verify(slackPoster).postMessage(anyString(), eq(BOT_TOKEN), anyString());
  }

  @Test
  void shouldPostOneMessagePerAlert() {
    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(
            GrafanaAlert.builder().status("firing")
                .labels(Map.of("alertname", "A")).annotations(Map.of("summary", "s1")).build(),
            GrafanaAlert.builder().status("firing")
                .labels(Map.of("alertname", "B")).annotations(Map.of("summary", "s2")).build(),
            GrafanaAlert.builder().status("firing")
                .labels(Map.of("alertname", "C")).annotations(Map.of("summary", "s3")).build()))
        .build();

    service.handleAlert(req).blockingAwait();

    verify(slackPoster, times(3)).postMessage(anyString(), anyString(), anyString());
  }

  // ---------- edge cases ----------

  @Test
  void shouldCompleteWithoutSlackCallWhenAlertsNull() {
    service.handleAlert(GrafanaWebhookRequest.builder().status("firing").build()).blockingAwait();

    verify(slackPoster, never()).postMessage(anyString(), anyString(), anyString());
  }

  @Test
  void shouldCompleteWithoutSlackCallWhenAlertsEmpty() {
    service.handleAlert(
        GrafanaWebhookRequest.builder().status("firing").alerts(List.of()).build())
        .blockingAwait();

    verify(slackPoster, never()).postMessage(anyString(), anyString(), anyString());
  }

  @Test
  void shouldCompleteForNullRequest() {
    service.handleAlert(null).blockingAwait();

    verify(slackPoster, never()).postMessage(anyString(), anyString(), anyString());
  }

  @Test
  void shouldFallBackToChannelMentionWhenOnCallReturnsNa() {
    mentionReturn.set(Single.just("N/A"));

    service.handleAlert(buildRequest("firing", "X", "y")).blockingAwait();

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(slackPoster).postMessage(anyString(), anyString(), textCaptor.capture());
    assertThat(textCaptor.getValue()).contains("<!channel>");
  }

  @Test
  void shouldUseDefaultAlertNameWhenLabelMissing() {
    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder().status("firing").build()))
        .build();

    service.handleAlert(req).blockingAwait();

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(slackPoster).postMessage(anyString(), anyString(), textCaptor.capture());
    assertThat(textCaptor.getValue()).contains("Alert");
  }

  @Test
  void shouldNotIncludeViewLinkWhenGeneratorUrlMissing() {
    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder()
            .status("firing")
            .labels(Map.of("alertname", "X"))
            .annotations(Map.of("summary", "y"))
            .build()))
        .build();

    service.handleAlert(req).blockingAwait();

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(slackPoster).postMessage(anyString(), anyString(), textCaptor.capture());
    assertThat(textCaptor.getValue()).doesNotContain("View in Grafana");
  }

  @Test
  void shouldUseDescriptionWhenSummaryMissing() {
    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder()
            .status("firing")
            .labels(Map.of("alertname", "X"))
            .annotations(Map.of("description", "fallback desc"))
            .build()))
        .build();

    service.handleAlert(req).blockingAwait();

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(slackPoster).postMessage(anyString(), anyString(), textCaptor.capture());
    assertThat(textCaptor.getValue()).contains("fallback desc");
  }

  @Test
  void shouldSkipPostingWhenBotTokenMissing() {
    notificationConfig.getIncident().getGoAlert().setSlackBotToken(null);

    service.handleAlert(buildRequest("firing", "X", "y")).blockingAwait();

    verify(slackPoster, never()).postMessage(anyString(), anyString(), anyString());
  }

  @Test
  void shouldSkipPostingWhenChannelMissing() {
    notificationConfig.getAlerts().getGrafana().setSlackChannelId(null);

    service.handleAlert(buildRequest("firing", "X", "y")).blockingAwait();

    verify(slackPoster, never()).postMessage(anyString(), anyString(), anyString());
  }

  // ---------- failure path ----------

  @Test
  void shouldPostFallbackMessageOnOnCallError() {
    mentionReturn.set(Single.error(new RuntimeException("goalert down")));

    service.handleAlert(buildRequest("firing", "X", "y")).blockingAwait();

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(slackPoster).postMessage(eq(FALLBACK_CHANNEL), anyString(), textCaptor.capture());
    assertThat(textCaptor.getValue()).contains("<!channel>").contains("alert proxy failed");
  }

  @Test
  void shouldCompleteEvenWhenSlackPosterFails() {
    when(slackPoster.postMessage(anyString(), anyString(), anyString()))
        .thenReturn(Single.error(new RuntimeException("slack 500")));

    // Should not throw
    service.handleAlert(buildRequest("firing", "X", "y")).blockingAwait();
  }

  @Test
  void shouldUseDefaultChannelAsFallbackWhenGrafanaFallbackMissing() {
    notificationConfig.getAlerts().getGrafana().setFallbackSlackChannelId(null);
    mentionReturn.set(Single.error(new RuntimeException("err")));

    service.handleAlert(buildRequest("firing", "X", "y")).blockingAwait();

    verify(slackPoster).postMessage(eq(ALERTS_CHANNEL), anyString(), anyString());
  }
}
