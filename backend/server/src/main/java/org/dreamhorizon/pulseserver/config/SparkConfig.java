package org.dreamhorizon.pulseserver.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for Spark jobs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SparkConfig {
  /**
   * Path to the Spark job jar.
   */
  private String jobJarPath;
  /**
   * Main class for the funnels batch job.
   */
  private String funnelsMainClass;
  /**
   * Main class for the journeys batch job.
   */
  private String journeysMainClass;
  /**
   * Main class for the events batch job.
   */
  private String eventsMainClass;
}
