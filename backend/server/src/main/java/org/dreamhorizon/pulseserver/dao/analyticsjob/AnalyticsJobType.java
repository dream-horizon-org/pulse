package org.dreamhorizon.pulseserver.dao.analyticsjob;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Types of analytics jobs stored in {@code analytics_jobs}.
 */
@Getter
@RequiredArgsConstructor
public enum AnalyticsJobType {
  /** Funnels daily batch job. */
  FUNNELS_DAILY("funnels-daily-batch"),
  /** Journeys daily batch job. */
  JOURNEYS_DAILY("journeys-daily-batch"),
  /** Events incremental batch job. */
  EVENTS_INCREMENTAL("events-incremental-batch"),
  /** Funnel on-save job. */
  FUNNEL("funnel-onsave-"),
  /** Journey on-save job. */
  JOURNEY("journey-onsave-");

  /** Prefix for the job name. */
  private final String jobNamePrefix;
}
