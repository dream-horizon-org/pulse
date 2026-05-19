package org.dreamhorizon.pulseserver.service.alerts.grafana.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.dao.service.ServiceDao;
import org.dreamhorizon.pulseserver.dao.service.models.ServiceRow;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest.GrafanaAlert;
import org.dreamhorizon.pulseserver.service.alerts.grafana.GrafanaAlertService;
import org.dreamhorizon.pulseserver.service.alerts.grafana.SlackPoster;
import org.dreamhorizon.pulseserver.service.oncall.OnCallService;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class GrafanaAlertServiceImpl implements GrafanaAlertService {

  private static final String CHANNEL_FALLBACK_MENTION = "<!channel>";
  private static final String ON_CALL_FALLBACK = "N/A";
  private static final String EMOJI_FIRING = ":rotating_light:";
  private static final String EMOJI_RESOLVED = ":white_check_mark:";
  private static final String STATUS_FIRING = "firing";
  private static final String STATUS_RESOLVED = "resolved";

  private final OnCallService onCallService;
  private final SlackPoster slackPoster;
  private final NotificationConfig notificationConfig;
  private final ServiceDao serviceDao;

  @Override
  public Completable handleAlert(GrafanaWebhookRequest request) {
    if (request == null || request.getAlerts() == null || request.getAlerts().isEmpty()) {
      log.warn("Grafana webhook received with no alerts, ignoring");
      return Completable.complete();
    }

    return Flowable.fromIterable(request.getAlerts())
        .flatMapCompletable(this::processOneAlert)
        .onErrorResumeNext(error -> {
          log.error("Failed to process Grafana webhook", error);
          return sendFallback("alert proxy failed: " + error.getMessage());
        });
  }

  private Completable processOneAlert(GrafanaAlert alert) {
    String serviceName = extractServiceName(alert);

    Single<ServiceRow> serviceLookup = (serviceName != null && !serviceName.isBlank())
        ? serviceDao.getByServiceName(serviceName)
            .defaultIfEmpty(ServiceRow.builder().build())
        : Single.just(ServiceRow.builder().build());

    return serviceLookup.flatMapCompletable(service -> {
      String goalertServiceId = service.getGoalertServiceId();

      Single<String> onCallMentions = onCallService.getOnCallSlackMentions(goalertServiceId)
          .map(m -> ON_CALL_FALLBACK.equals(m) ? CHANNEL_FALLBACK_MENTION : m);

      Single<String> ownerMention = resolveOwnerMention(service);

      return Single.zip(onCallMentions, ownerMention, (oncall, owner) ->
              buildMessage(oncall, owner, alert))
          .flatMapCompletable(text -> {
            String channel = resolveChannelForAlert(alert);
            String botToken = resolveBotToken();
            if (channel == null || channel.isBlank() || botToken == null || botToken.isBlank()) {
              log.error("Slack channel or bot token not configured; cannot post alert");
              return Completable.complete();
            }
            return slackPoster.postMessage(channel, botToken, text)
                .ignoreElement()
                .onErrorComplete(error -> {
                  log.error("Slack post failed for alert", error);
                  return true;
                });
          });
    });
  }

  private String extractServiceName(GrafanaAlert alert) {
    Map<String, String> labels = alert.getLabels();
    return labels != null ? labels.get("service") : null;
  }

  private Single<String> resolveOwnerMention(ServiceRow service) {
    if (service.getOwnerEmail() == null || service.getOwnerEmail().isBlank()) {
      return Single.just("");
    }

    if (service.getOwnerSlackId() != null && !service.getOwnerSlackId().isBlank()) {
      return Single.just(String.format("<@%s>", service.getOwnerSlackId()));
    }

    String botToken = resolveBotToken();
    if (botToken == null || botToken.isBlank()) {
      return Single.just(service.getOwnerEmail());
    }

    return onCallService.lookupSlackUserId(service.getOwnerEmail(), botToken)
        .map(id -> (id.startsWith("U") || id.startsWith("W"))
            ? String.format("<@%s>", id)
            : service.getOwnerEmail())
        .onErrorReturnItem(service.getOwnerEmail());
  }

  private String buildMessage(String onCallMention, String ownerMention, GrafanaAlert alert) {
    Map<String, String> labels = alert.getLabels() != null ? alert.getLabels() : Map.of();
    Map<String, String> annotations =
        alert.getAnnotations() != null ? alert.getAnnotations() : Map.of();

    String status = alert.getStatus() != null ? alert.getStatus() : STATUS_FIRING;
    String emoji = STATUS_RESOLVED.equalsIgnoreCase(status) ? EMOJI_RESOLVED : EMOJI_FIRING;
    String statusLabel = status.toUpperCase();
    String alertName = labels.getOrDefault("alertname", "Alert");
    String summary = annotations.getOrDefault(
        "summary", annotations.getOrDefault("description", ""));
    String url = alert.getGeneratorURL();

    StringBuilder sb = new StringBuilder();
    sb.append(emoji).append(' ').append(statusLabel).append(": *").append(alertName).append("*\n");
    if (!summary.isEmpty()) {
      sb.append(summary).append('\n');
    }
    sb.append(":bell: On-call: ").append(onCallMention).append('\n');
    if (!ownerMention.isEmpty()) {
      sb.append(":bust_in_silhouette: Service Owner: ").append(ownerMention).append('\n');
    }
    if (url != null && !url.isBlank()) {
      sb.append('<').append(url).append("|View in Grafana>");
    }
    return sb.toString();
  }

  private String resolveChannelForAlert(GrafanaAlert alert) {
    NotificationConfig.GrafanaAlertsConfig grafana = grafanaConfig();
    if (grafana == null) {
      return null;
    }

    // Priority 1: route match from config
    List<NotificationConfig.GrafanaRouteConfig> routes = grafana.getRoutes();
    Map<String, String> labels = alert.getLabels();
    if (routes != null && !routes.isEmpty() && labels != null) {
      for (NotificationConfig.GrafanaRouteConfig route : routes) {
        if (matchesRoute(labels, route.getMatchers())) {
          log.debug("Alert matched route '{}' -> channel {}", route.getName(),
              route.getSlackChannelId());
          return route.getSlackChannelId();
        }
      }
    }

    // Priority 2: default
    return grafana.getSlackChannelId();
  }

  private boolean matchesRoute(Map<String, String> labels, Map<String, String> matchers) {
    if (matchers == null || matchers.isEmpty()) {
      return false;
    }
    return matchers.entrySet().stream()
        .allMatch(entry -> entry.getValue().equals(labels.get(entry.getKey())));
  }

  private Completable sendFallback(String reason) {
    String channel = resolveFallbackChannel();
    String botToken = resolveBotToken();
    if (channel == null || channel.isBlank() || botToken == null || botToken.isBlank()) {
      log.error("Cannot send fallback message — channel or bot token missing");
      return Completable.complete();
    }
    String text = CHANNEL_FALLBACK_MENTION + " :warning: " + reason;
    return slackPoster.postMessage(channel, botToken, text)
        .ignoreElement()
        .onErrorComplete(error -> {
          log.error("Fallback Slack post also failed", error);
          return true;
        });
  }

  private NotificationConfig.GrafanaAlertsConfig grafanaConfig() {
    NotificationConfig.AlertsConfig alerts = notificationConfig.getAlertsConfig();
    return alerts != null ? alerts.getGrafana() : null;
  }

  private String resolveFallbackChannel() {
    NotificationConfig.GrafanaAlertsConfig grafana = grafanaConfig();
    if (grafana == null) {
      return null;
    }
    if (grafana.getFallbackSlackChannelId() != null
        && !grafana.getFallbackSlackChannelId().isBlank()) {
      return grafana.getFallbackSlackChannelId();
    }
    return grafana.getSlackChannelId();
  }

  private String resolveBotToken() {
    NotificationConfig.IncidentConfig incident = notificationConfig.getIncidentConfig();
    if (incident == null || incident.getGoAlert() == null) {
      return null;
    }
    return incident.getGoAlert().getSlackBotToken();
  }
}
