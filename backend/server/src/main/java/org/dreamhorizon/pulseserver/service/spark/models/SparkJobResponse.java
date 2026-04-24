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

    private String jobName;
    /** Echo of submitted main artifact URI. */
    private String entryPoint;
    /** Echo of submitted main class (FQCN), if any. */
    private String mainClass;
    /** ISO-8601 instant when the job was submitted (e.g. {@code Instant.now().toString()}). */
    private String submittedAt;
}
