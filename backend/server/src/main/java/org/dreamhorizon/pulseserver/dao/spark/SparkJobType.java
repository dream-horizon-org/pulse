package org.dreamhorizon.pulseserver.dao.spark;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum representing the different types of Spark jobs.
 */
@Getter
@RequiredArgsConstructor
public enum SparkJobType {
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
