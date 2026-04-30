package org.dreamhorizon.pulsealertscron.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.inject.Singleton;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationConfig {
  private String pulseServerUrl;
  private long shutdownGracePeriod;
  private String serviceJwtSecret;

  /** Seconds between usage-credits → Redis enqueue calls (optional; default 5). */
  private Integer usageCreditsSyncIntervalSeconds;

  /** Seconds between API-key → Redis enqueue calls (optional; default 600). */
  private Integer apiKeysSyncIntervalSeconds;

  /** Seconds between usage-limit notification batch enqueue calls (optional; default 86400 = 24h). */
  private Integer usageLimitNotificationIntervalSeconds;

  public static final int DEFAULT_USAGE_CREDITS_SYNC_INTERVAL_SECONDS = 5;
  public static final int DEFAULT_API_KEYS_SYNC_INTERVAL_SECONDS = 600;
  public static final int DEFAULT_USAGE_LIMIT_NOTIFICATION_INTERVAL_SECONDS = 24 * 60 * 60;

  /** Minimum interval to avoid accidental tight loops in config. */
  public static final int MIN_SYNC_INTERVAL_SECONDS = 5;

  public int resolveUsageCreditsSyncIntervalSeconds() {
    if (usageCreditsSyncIntervalSeconds == null) {
      return DEFAULT_USAGE_CREDITS_SYNC_INTERVAL_SECONDS;
    }
    return Math.max(MIN_SYNC_INTERVAL_SECONDS, usageCreditsSyncIntervalSeconds);
  }

  public int resolveApiKeysSyncIntervalSeconds() {
    if (apiKeysSyncIntervalSeconds == null) {
      return DEFAULT_API_KEYS_SYNC_INTERVAL_SECONDS;
    }
    return Math.max(MIN_SYNC_INTERVAL_SECONDS, apiKeysSyncIntervalSeconds);
  }

  public int resolveUsageLimitNotificationIntervalSeconds() {
    if (usageLimitNotificationIntervalSeconds == null) {
      return DEFAULT_USAGE_LIMIT_NOTIFICATION_INTERVAL_SECONDS;
    }
    return Math.max(MIN_SYNC_INTERVAL_SECONDS, usageLimitNotificationIntervalSeconds);
  }

  // Batch job configuration
  private String batchFunnelsEndpoint = "/internal/analytics/funnels";
  private String batchJourneysEndpoint = "/internal/analytics/journeys"; 
  private String batchEventsEndpoint = "/internal/analytics/events";
  private String batchScheduleTime = "02:00"; // UTC time in HH:mm format
  private boolean batchJobsEnabled = true;
}
