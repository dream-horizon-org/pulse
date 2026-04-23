package org.dreamhorizon.pulseserver.dao.analyticsjob;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for a row in {@code analytics_jobs}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsJobEntity {
  /** Internal database ID. */
  private Long id;
  /** Type of job. */
  private AnalyticsJobType jobType;
  /** ID of the reference entity. */
  private Long referenceId;
  /** EMR job run ID (when applicable). */
  private String jobId;
  /** Job status. */
  private AnalyticsJobStatus status;
  /** Error message if failed. */
  private String errorMessage;
  /** Time the job started. */
  private LocalDateTime startedAt;
  /** Time the job completed. */
  private LocalDateTime completedAt;
  /** Time the record was created. */
  private LocalDateTime createdAt;
}
