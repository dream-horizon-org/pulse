package org.dreamhorizon.pulseserver.service.alerts.grafana.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest.GrafanaAlert;
import org.dreamhorizon.pulseserver.service.alerts.grafana.GrafanaAlertService;
import org.dreamhorizon.pulseserver.service.alerts.grafana.SlackPoster;
import org.dreamhorizon.pulseserver.service.oncall.OnCallService;

/**
 * Default implementation. Pipeline per webhook:
 *
 * <ol>
 *   <li>Ask {@link OnCallService#getOnCallSlackMentions()} for the formatted {@code <@U...>}
 *       mention(s). Returns {@code "N/A"} if the lookup fails or no email/Slack match exists —
 *       in that case we fall back to {@code <!channel>} so a human still sees the page.
 *   <li>For each alert in the payload, build a Slack message (mention + status emoji + alertname
 *       + summary + Grafana link) and POST via {@link SlackPoster}.
 *   <li>If any step fails, post a {@code <!channel>} fallback to the Grafana fallback channel
 *       (or default alerts channel) describing the failure. Never throw.
 * </ol>
 *
 * <p>This service is stateless — no DB writes, no incident creation, no acknowledgment.
 * It is purely a notification fan-out enriched with on-call context.
 */
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

  @Override
  public Completable handleAlert(GrafanaWebhookRequest request) {
    if (request == null || request.getAlerts() == null || request.getAlerts().isEmpty()) {
      log.warn("Grafana webhook received with no alerts, ignoring");
      return Completable.complete();
    }

    return onCallService.getOnCallSlackMentions()
        .map(mention -> ON_CALL_FALLBACK.equals(mention) ? CHANNEL_FALLBACK_MENTION : mention)
        .flatMapCompletable(mention -> sendAllAlerts(mention, request.getAlerts()))
        .onErrorResumeNext(error -> {
          log.error("Failed to process Grafana webhook", error);
          return sendFallback("alert proxy failed: " + error.getMessage());
        });
  }

  private Completable sendAllAlerts(String mention, List<GrafanaAlert> alerts) {
    return Flowable.fromIterable(alerts)
        .flatMapCompletable(alert -> sendOneAlert(mention, alert));
  }

  private Completable sendOneAlert(String mention, GrafanaAlert alert) {
    String channel = resolveChannelForAlert(alert);
    String botToken = resolveBotToken();
    if (channel == null || channel.isBlank() || botToken == null || botToken.isBlank()) {
      log.error("Slack channel or bot token not configured; cannot post alert");
      return Completable.complete();
    }
    String text = buildMessage(mention, alert);
    return slackPoster.postMessage(channel, botToken, text)
        .ignoreElement()
        .onErrorComplete(error -> {
          log.error("Slack post failed for alert", error);
          return true;
        });
  }

  private String buildMessage(String mention, GrafanaAlert alert) {
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
    sb.append(mention).append(' ').append(emoji).append(' ')
        .append(statusLabel).append(": *").append(alertName).append("*\n");
    if (!summary.isEmpty()) {
      sb.append(summary).append('\n');
    }
    if (url != null && !url.isBlank()) {
      sb.append('<').append(url).append("|View in Grafana>");
    }
    return sb.toString();
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

  private String resolveChannelForAlert(GrafanaAlert alert) {
    NotificationConfig.GrafanaAlertsConfig grafana = grafanaConfig();
    if (grafana == null) {
      return null;
    }
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
    return grafana.getSlackChannelId();
  }

  private boolean matchesRoute(Map<String, String> labels, Map<String, String> matchers) {
    if (matchers == null || matchers.isEmpty()) {
      return false;
    }
    return matchers.entrySet().stream()
        .allMatch(entry -> entry.getValue().equals(labels.get(entry.getKey())));
  }

  private String resolveChannel() {
    NotificationConfig.GrafanaAlertsConfig grafana = grafanaConfig();
    return grafana != null ? grafana.getSlackChannelId() : null;
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
    // Reuse the bot token configured for the GoAlert-on-call lookups (same Slack app).
    NotificationConfig.IncidentConfig incident = notificationConfig.getIncidentConfig();
    if (incident == null || incident.getGoAlert() == null) {
      return null;
    }
    return incident.getGoAlert().getSlackBotToken();
  }
}
