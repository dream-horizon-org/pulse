package org.dreamhorizon.pulseserver.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for the analytics compute engine.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsEngineConfig {

  /**
   * Compute engine to use: "spark" or "clickhouse".
   */
  private String computeEngine;

  /**
   * Number of project-level batch queries that may run concurrently against ClickHouse.
   * Mandatory in production when computeEngine is "clickhouse".
   */
  private int batchProjectConcurrency;

  /**
   * Returns true when the compute engine is configured to use ClickHouse.
   */
  public boolean isClickHouseEngine() {
    return "clickhouse".equalsIgnoreCase(computeEngine);
  }
}
