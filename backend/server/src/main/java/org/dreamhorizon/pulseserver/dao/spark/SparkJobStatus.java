package org.dreamhorizon.pulseserver.dao.spark;

/**
 * Enum representing the status of a Spark job.
 */
public enum SparkJobStatus {
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
