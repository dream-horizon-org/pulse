package org.dreamhorizon.pulseserver.service.spark.models;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Request to submit a Spark job.
 */
@Getter
@Builder
public class SparkJobRequest {

  /** Name of the job. */
  private String jobName;

  /** Spark jar main class. */
  private String mainClass;

  /** Location of the spark jar. */
  private String entryPoint;

  /** Job arguments. */
  private List<String> arguments;

  /** Spark parameters. */
  private String sparkConfig;

  /** Timeout in minutes. */
  private Long timeoutMinutes;

  /** Tags to apply to the job. */
  private Map<String, String> tags;
}
