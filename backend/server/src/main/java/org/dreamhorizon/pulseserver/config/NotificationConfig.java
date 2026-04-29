package org.dreamhorizon.pulseserver.config;

import com.google.inject.Singleton;
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
  private EmailNotificationDefaults email;

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

  /**
   * SES From for new EMAIL channels when the client omits fromAddress / fromName.
   * Set via {@code DEFAULT_ALERT_EMAIL_FROM_ADDRESS} / {@code DEFAULT_ALERT_EMAIL_FROM_NAME}.
   */
  public String resolveDefaultAlertEmailFromAddress() {
    if (email != null
        && email.getDefaultFromAddress() != null
        && !email.getDefaultFromAddress().isBlank()) {
      return email.getDefaultFromAddress();
    }
    return "alerts@pulse-ux.com";
  }

  /** Display name paired with {@link #resolveDefaultAlertEmailFromAddress()}. */
  public String resolveDefaultAlertEmailFromName() {
    if (email != null
        && email.getDefaultFromName() != null
        && !email.getDefaultFromName().isBlank()) {
      return email.getDefaultFromName();
    }
    return "Pulse Alerts";
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class EmailNotificationDefaults {
    private String defaultFromAddress;
    private String defaultFromName;
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
