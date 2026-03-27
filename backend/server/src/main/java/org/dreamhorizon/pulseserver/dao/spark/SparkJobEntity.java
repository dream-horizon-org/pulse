package org.dreamhorizon.pulseserver.dao.spark;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a Spark job in the database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SparkJobEntity {
  /** Internal database ID. */
  private Long id;
  /** Type of job (FUNNEL, JOURNEY). */
  private String jobType;
  /** ID of the reference entity. */
  private Long referenceId;
  /** EMR job run ID. */
  private String jobId;
  /** Job status. */
  private String status;
  /** Error message if failed. */
  private String errorMessage;
  /** Time the job started. */
  private LocalDateTime startedAt;
  /** Time the job completed. */
  private LocalDateTime completedAt;
  /** Time the record was created. */
  private LocalDateTime createdAt;
}
