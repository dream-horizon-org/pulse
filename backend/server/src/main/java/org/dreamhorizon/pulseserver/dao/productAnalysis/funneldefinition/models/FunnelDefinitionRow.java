package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FunnelDefinitionRow {
  long id;
  String projectId;
  String name;
  String description;
  String funnelType;
  String stepOrderType;
  String stepsJson;
  long windowSeconds;
  String mode;
  String filtersJson;
  int dateRangeDays;
  Instant startTime;
  Instant endTime;
  Instant expiry;
  /**
   * Null on insert before read-back.
   */
  Instant createdAt;
  /**
   * Null on insert before read-back.
   */
  Instant updatedAt;
  String createdBy;
  /**
   * Latest spark_jobs.status for FUNNEL, or null if no job.
   */
  String latestJobStatus;
}
