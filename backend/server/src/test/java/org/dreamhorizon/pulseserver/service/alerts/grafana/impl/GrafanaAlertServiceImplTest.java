package org.dreamhorizon.pulseserver.service.alerts.grafana.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.dao.service.ServiceDao;
import org.dreamhorizon.pulseserver.dao.service.models.ServiceRow;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest.GrafanaAlert;
import org.dreamhorizon.pulseserver.service.alerts.grafana.SlackPoster;
import org.dreamhorizon.pulseserver.service.oncall.OnCallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GrafanaAlertServiceImplTest {

  private static final String ALERTS_CHANNEL = "C123ALERT";
  private static final String FALLBACK_CHANNEL = "C999FALL";
  private static final String BOT_TOKEN = "xoxb-test-token";
  private static final String SLACK_MENTION = "<@U07YASIR>";

  private AtomicReference<Single<String>> mentionReturn;
  private SlackPoster slackPoster;
  private NotificationConfig notificationConfig;
  private ServiceDao serviceDao;
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

    serviceDao = mock(ServiceDao.class);
    when(serviceDao.getByServiceName(anyString())).thenReturn(Maybe.empty());

    OnCallService onCallStub = new OnCallService(null, null, notificationConfig) {
      @Override
      public Single<String> getOnCallSlackMentions() {
        return mentionReturn.get();
      }

      @Override
      public Single<String> getOnCallSlackMentions(String goalertServiceId) {
        return mentionReturn.get();
      }
    };

    service = new GrafanaAlertServiceImpl(onCallStub, slackPoster, notificationConfig, serviceDao);
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
  void shouldPostFallbackMessageOnProcessingError() {
    when(serviceDao.getByServiceName(anyString()))
        .thenReturn(Maybe.error(new RuntimeException("db down")));

    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder()
            .status("firing")
            .labels(Map.of("alertname", "X", "service", "payment-service"))
            .annotations(Map.of("summary", "y"))
            .build()))
        .build();

    service.handleAlert(req).blockingAwait();

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(slackPoster).postMessage(eq(FALLBACK_CHANNEL), anyString(), textCaptor.capture());
    assertThat(textCaptor.getValue()).contains("<!channel>").contains("alert proxy failed");
  }

  @Test
  void shouldCompleteEvenWhenSlackPosterFails() {
    when(slackPoster.postMessage(anyString(), anyString(), anyString()))
        .thenReturn(Single.error(new RuntimeException("slack 500")));

    service.handleAlert(buildRequest("firing", "X", "y")).blockingAwait();
  }

  @Test
  void shouldUseDefaultChannelAsFallbackWhenGrafanaFallbackMissing() {
    notificationConfig.getAlerts().getGrafana().setFallbackSlackChannelId(null);
    when(serviceDao.getByServiceName(anyString()))
        .thenReturn(Maybe.error(new RuntimeException("err")));

    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder()
            .status("firing")
            .labels(Map.of("alertname", "X", "service", "svc"))
            .annotations(Map.of("summary", "y"))
            .build()))
        .build();

    service.handleAlert(req).blockingAwait();

    verify(slackPoster).postMessage(eq(ALERTS_CHANNEL), anyString(), anyString());
  }

  // ---------- label-based routing ----------

  private static final String SPM_CHANNEL = "C_SPM";
  private static final String OPERATOR_CHANNEL = "C_OPS";

  private void configureRoutes() {
    NotificationConfig.GrafanaRouteConfig spmRoute = new NotificationConfig.GrafanaRouteConfig();
    spmRoute.setName("spm-alerts");
    spmRoute.setMatchers(Map.of("severity", "spm"));
    spmRoute.setSlackChannelId(SPM_CHANNEL);

    NotificationConfig.GrafanaRouteConfig opsRoute = new NotificationConfig.GrafanaRouteConfig();
    opsRoute.setName("operator-alerts");
    opsRoute.setMatchers(Map.of("severity", "operator"));
    opsRoute.setSlackChannelId(OPERATOR_CHANNEL);

    notificationConfig.getAlerts().getGrafana().setRoutes(List.of(spmRoute, opsRoute));
  }

  @Test
  void shouldRouteSpmAlertToSpmChannel() {
    configureRoutes();
    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder()
            .status("firing")
            .labels(Map.of("alertname", "HighLatency", "severity", "spm"))
            .annotations(Map.of("summary", "latency high"))
            .build()))
        .build();

    service.handleAlert(req).blockingAwait();

    verify(slackPoster).postMessage(eq(SPM_CHANNEL), eq(BOT_TOKEN), anyString());
  }

  @Test
  void shouldRouteOperatorAlertToOperatorChannel() {
    configureRoutes();
    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder()
            .status("firing")
            .labels(Map.of("alertname", "DiskFull", "severity", "operator"))
            .annotations(Map.of("summary", "disk 95%"))
            .build()))
        .build();

    service.handleAlert(req).blockingAwait();

    verify(slackPoster).postMessage(eq(OPERATOR_CHANNEL), eq(BOT_TOKEN), anyString());
  }

  @Test
  void shouldFallBackToDefaultChannelWhenNoRouteMatches() {
    configureRoutes();
    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder()
            .status("firing")
            .labels(Map.of("alertname", "Unknown", "severity", "critical"))
            .annotations(Map.of("summary", "something"))
            .build()))
        .build();

    service.handleAlert(req).blockingAwait();

    verify(slackPoster).postMessage(eq(ALERTS_CHANNEL), eq(BOT_TOKEN), anyString());
  }

  @Test
  void shouldFallBackToDefaultChannelWhenLabelsNull() {
    configureRoutes();
    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder()
            .status("firing")
            .build()))
        .build();

    service.handleAlert(req).blockingAwait();

    verify(slackPoster).postMessage(eq(ALERTS_CHANNEL), eq(BOT_TOKEN), anyString());
  }

  @Test
  void shouldRouteMultipleAlertsToRespectiveChannels() {
    configureRoutes();
    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(
            GrafanaAlert.builder().status("firing")
                .labels(Map.of("alertname", "A", "severity", "spm"))
                .annotations(Map.of("summary", "s")).build(),
            GrafanaAlert.builder().status("firing")
                .labels(Map.of("alertname", "B", "severity", "operator"))
                .annotations(Map.of("summary", "s")).build()))
        .build();

    service.handleAlert(req).blockingAwait();

    verify(slackPoster).postMessage(eq(SPM_CHANNEL), eq(BOT_TOKEN), anyString());
    verify(slackPoster).postMessage(eq(OPERATOR_CHANNEL), eq(BOT_TOKEN), anyString());
  }

  // ---------- service-based routing ----------

  @Test
  void shouldIncludeServiceOwnerMentionWhenServiceFound() {
    ServiceRow paymentService = ServiceRow.builder()
        .serviceName("payment-service")
        .ownerEmail("owner@example.com")
        .ownerSlackId("U_OWNER")
        .goalertServiceId("goalert-payment-uuid")
        .build();
    when(serviceDao.getByServiceName("payment-service")).thenReturn(Maybe.just(paymentService));

    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder()
            .status("firing")
            .labels(Map.of("alertname", "PaymentTimeout", "service", "payment-service"))
            .annotations(Map.of("summary", "timeouts increasing"))
            .build()))
        .build();

    service.handleAlert(req).blockingAwait();

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(slackPoster).postMessage(eq(ALERTS_CHANNEL), eq(BOT_TOKEN), textCaptor.capture());
    String text = textCaptor.getValue();
    assertThat(text).contains(":bell: On-call:");
    assertThat(text).contains(":bust_in_silhouette: Service Owner: <@U_OWNER>");
  }

  @Test
  void shouldUseDefaultChannelWhenNoRouteMatch() {
    ServiceRow authService = ServiceRow.builder()
        .serviceName("auth-service")
        .ownerEmail("auth-owner@example.com")
        .ownerSlackId("U_AUTH")
        .build();
    when(serviceDao.getByServiceName("auth-service")).thenReturn(Maybe.just(authService));

    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder()
            .status("firing")
            .labels(Map.of("alertname", "AuthFail", "service", "auth-service"))
            .annotations(Map.of("summary", "auth errors"))
            .build()))
        .build();

    service.handleAlert(req).blockingAwait();

    verify(slackPoster).postMessage(eq(ALERTS_CHANNEL), eq(BOT_TOKEN), anyString());
  }

  @Test
  void shouldNotIncludeOwnerLineWhenServiceNotInDb() {
    service.handleAlert(buildRequest("firing", "CpuHigh", "CPU > 80%")).blockingAwait();

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(slackPoster).postMessage(anyString(), anyString(), textCaptor.capture());
    assertThat(textCaptor.getValue()).doesNotContain("Service Owner:");
  }

  @Test
  void shouldRouteConfigTakesPriorityOverServiceChannel() {
    configureRoutes();
    ServiceRow svc = ServiceRow.builder()
        .serviceName("spm-svc")
        .ownerEmail("spm@example.com")
        .ownerSlackId("U_SPM")
        .build();
    when(serviceDao.getByServiceName("spm-svc")).thenReturn(Maybe.just(svc));

    GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
        .status("firing")
        .alerts(List.of(GrafanaAlert.builder()
            .status("firing")
            .labels(Map.of("alertname", "X", "severity", "spm", "service", "spm-svc"))
            .annotations(Map.of("summary", "s"))
            .build()))
        .build();

    service.handleAlert(req).blockingAwait();

    // Route match (SPM_CHANNEL) takes priority over default channel
    verify(slackPoster).postMessage(eq(SPM_CHANNEL), eq(BOT_TOKEN), anyString());
  }
}
