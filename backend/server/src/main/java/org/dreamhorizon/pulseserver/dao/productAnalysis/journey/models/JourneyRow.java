package org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JourneyRow {
  long id;
  String projectId;
  String name;
  String description;
  String anchorEvent;
  String direction;
  int depth;
  String mode;
  String filtersJson;
  Instant startTime;
  Instant endTime;
  String journeyType;
  Instant expiry;
  int dateRangeDays;
  Instant createdAt;
  Instant updatedAt;
  String createdBy;
  /**
   * Latest analytics_jobs.status for JOURNEY, or null if no job.
   */
  String latestJobStatus;
  /**
   * Total matching rows before pagination (populated from COUNT(*) OVER() in list queries; 0 otherwise).
   */
  long totalCount;
}
