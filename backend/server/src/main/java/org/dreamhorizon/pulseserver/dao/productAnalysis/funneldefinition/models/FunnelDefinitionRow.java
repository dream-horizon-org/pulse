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
   * Latest analytics_jobs.status for FUNNEL, or null if no job.
   */
  String latestJobStatus;
  /**
   * Total matching rows before pagination (populated from COUNT(*) OVER() in list queries; 0 otherwise).
   */
  long totalCount;
  /**
   * Optional key inside {@code LogAttributes} (or {@code props} on the Spark path) holding the order's
   * numeric value, used to compute revenue / AOV / lost-revenue per step. {@code null} disables revenue.
   */
  String revenueAttribute;
  /**
   * Optional 0-based index of the step that carries the order/purchase event. Defaults to the last step
   * when {@code null} and {@link #revenueAttribute} is set.
   */
  Integer revenueStepIndex;
  /**
   * Display-only ISO-4217 currency code (e.g. {@code INR}, {@code USD}). Has no effect on computation.
   */
  String currency;
}
