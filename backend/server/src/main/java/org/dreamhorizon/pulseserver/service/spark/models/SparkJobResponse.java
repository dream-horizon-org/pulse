package org.dreamhorizon.pulseserver.service.spark.models;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Response from submitting a Spark job.
 */
@Getter
@Builder
public class SparkJobResponse {

  /** EMR application ID. */
  private String applicationId;

  /** EMR job run ID. */
  private String jobRunId;

  /** EMR job run ARN. */
  private String arn;

  /** Name of the job. */
  private String jobName;

  /** Spark jar main class. */
  private String mainClass;

  /** Time the job was submitted. */
  private LocalDateTime submittedAt;
}
