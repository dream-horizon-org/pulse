package org.dreamhorizon.pulseserver.config;

import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Singleton
public class NotificationConfig {
  private AwsConfig aws;
  private SqsConfig sqs;
  private SesConfig ses;
  private RetryConfig retry;
  private WorkerConfig worker;
  private SlackOAuthConfig slackOAuth;
  private IncidentConfig incident;
  private AlertsConfig alerts;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AwsConfig {
    private String region = "us-east-1";
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SqsConfig {
    private String queueUrl;
    private String dlqUrl;
    private int visibilityTimeoutSeconds = 300;
    private int waitTimeSeconds = 20;
    private int maxReceiveCount = 3;

    public boolean isEnabled() {
      return queueUrl != null && !queueUrl.isBlank();
    }

    public boolean isDlqEnabled() {
      return dlqUrl != null && !dlqUrl.isBlank();
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SesConfig {
    private String configurationSetName;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RetryConfig {
    private int maxAttempts = 3;
    private long initialDelayMs = 1000;
    private long maxDelayMs = 30000;
    private double multiplier = 2.0;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class WorkerConfig {
    private int batchSize = 10;
    private int visibilityTimeoutSeconds = 60;
    private int pollIntervalSeconds = 1;
    private boolean enabled = true;
  }

  public String getRegion() {
    return aws != null && aws.getRegion() != null ? aws.getRegion() : "ap-south-1";
  }

  public boolean isSqsEnabled() {
    return sqs != null && sqs.isEnabled();
  }

  public boolean isDlqEnabled() {
    return sqs != null && sqs.isDlqEnabled();
  }

  public RetryConfig getRetryConfig() {
    return retry != null ? retry : new RetryConfig();
  }

  public WorkerConfig getWorkerConfig() {
    return worker != null ? worker : new WorkerConfig();
  }

  public SlackOAuthConfig getSlackOAuthConfig() {
    return slackOAuth != null ? slackOAuth : new SlackOAuthConfig();
  }

  public IncidentConfig getIncidentConfig() {
    return incident != null ? incident : new IncidentConfig();
  }

  public AlertsConfig getAlertsConfig() {
    return alerts != null ? alerts : new AlertsConfig();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class IncidentConfig {
    private String defaultSlackChannelId;
    private String onCallProvider = "GO_ALERT";
    private GoAlertConfig goAlert;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GoAlertConfig {
    private String goAlertUrl;
    private String goAlertApiKey;
    private String goAlertUserApiKey;
    private String goAlertServiceId;
    private String slackBotToken;
  }

  /**
   * Configuration for stateless alert pass-throughs (e.g. Grafana webhook).
   * Distinct from {@link IncidentConfig} — alerts here do NOT create incidents
   * or participate in ack/recover/close workflows.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AlertsConfig {
    private GrafanaAlertsConfig grafana;
  }

  /**
   * Grafana webhook integration config. The webhook endpoint itself lives at
   * {@code /v1/alerts/grafana/webhook} (no auth currently — restrict via
   * network/SG).
   *
   * <ul>
   *   <li>{@code slackChannelId} — primary channel where on-call-tagged messages are posted.
   *   <li>{@code fallbackSlackChannelId} — where {@code @channel} warnings go if on-call
   *       lookup fails; if unset, falls back to {@code slackChannelId}.
   * </ul>
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GrafanaAlertsConfig {
    private String slackChannelId;
    private String fallbackSlackChannelId;
    private List<GrafanaRouteConfig> routes;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GrafanaRouteConfig {
    private String name;
    private Map<String, String> matchers;
    private String slackChannelId;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SlackOAuthConfig {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String uiRedirectUrl;
    private String scopes = "chat:write,channels:read";

    public boolean isEnabled() {
      return clientId != null && !clientId.isBlank()
          && clientSecret != null && !clientSecret.isBlank();
    }
  }
}
