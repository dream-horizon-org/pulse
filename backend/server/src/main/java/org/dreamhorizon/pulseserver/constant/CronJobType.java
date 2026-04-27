package org.dreamhorizon.pulseserver.constant;

/** Values stored in {@code cron_jobs_history.job_type}. */
public final class CronJobType {

  public static final String API_KEYS_TO_REDIS = "API_KEYS_TO_REDIS";
  public static final String USAGE_CREDITS_TO_REDIS = "USAGE_CREDITS_TO_REDIS";
  public static final String USAGE_LIMIT_NOTIFICATIONS = "USAGE_LIMIT_NOTIFICATIONS";

  private CronJobType() {
  }
}
