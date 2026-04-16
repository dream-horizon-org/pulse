package org.dreamhorizon.pulseserver.dao.analyticsjob;

/**
 * Status of a row in {@code analytics_jobs} (Spark EMR, ClickHouse compute, etc.).
 */
public enum AnalyticsJobStatus {
  /** Job is pending submission. */
  PENDING,
  /** Job has been submitted to EMR. */
  SUBMITTED,
  /** Job is currently running. */
  RUNNING,
  /** Job has completed successfully. */
  SUCCEEDED,
  /** Job has failed. */
  FAILED
}
