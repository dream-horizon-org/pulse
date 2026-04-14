package org.dreamhorizon.pulseserver.dao.rcajob;

/** SQL for pulse_db.rca_report_jobs (MySQL). */
public final class RcaReportJobQueries {

  private RcaReportJobQueries() {
  }

  /**
   * Insert a new job. Params: job_id, project_id, interaction_name, date, status,
   * error_message, created_by, worker_instance_id, version.
   * {@code created_at} / {@code started_at} / {@code completed_at} use DB defaults.
   */
  public static final String INSERT_JOB =
      "INSERT INTO rca_report_jobs (job_id, project_id, interaction_name, date, status,"
          + " error_message, created_by, worker_instance_id, version)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

  public static final String GET_JOB_BY_ID =
      "SELECT job_id, project_id, interaction_name, date, status,"
          + " error_message, created_at, started_at, completed_at, created_by,"
          + " worker_instance_id, version"
          + " FROM rca_report_jobs WHERE job_id = ?";

  /**
   * Prefer an in-flight job over a queued duplicate. Params: project_id, interaction_name, date.
   */
  public static final String GET_ACTIVE_JOB_BY_KEY =
      "SELECT job_id, project_id, interaction_name, date, status,"
          + " error_message, created_at, started_at, completed_at, created_by,"
          + " worker_instance_id, version"
          + " FROM rca_report_jobs"
          + " WHERE project_id = ? AND interaction_name = ? AND date = ?"
          + " AND status IN ('PENDING', 'PROCESSING')"
          + " ORDER BY FIELD(status, 'PROCESSING', 'PENDING'), created_at ASC"
          + " LIMIT 1";

  /**
   * Params: status, status (for started_at branch), job_id.
   */
  public static final String UPDATE_STATUS =
      "UPDATE rca_report_jobs SET"
          + " status = ?,"
          + " started_at = IF(? = 'PROCESSING', COALESCE(started_at, CURRENT_TIMESTAMP(6)),"
          + " started_at),"
          + " version = version + 1"
          + " WHERE job_id = ?";

  /** Params: job_id. */
  public static final String UPDATE_COMPLETED =
      "UPDATE rca_report_jobs SET status = 'COMPLETED',"
          + " completed_at = CURRENT_TIMESTAMP(6),"
          + " error_message = NULL,"
          + " version = version + 1"
          + " WHERE job_id = ?";

  /** Params: error_message, job_id. */
  public static final String UPDATE_FAILED =
      "UPDATE rca_report_jobs SET status = 'FAILED',"
          + " error_message = ?,"
          + " completed_at = CURRENT_TIMESTAMP(6),"
          + " version = version + 1"
          + " WHERE job_id = ?";

  /**
   * Removes any previous COMPLETED row for the same logical key before the current job transitions
   * to COMPLETED. Required because uk_active_job covers all status values, so a prior completed
   * run would otherwise block the UPDATE via a unique-key conflict.
   * Params: project_id, interaction_name, date, job_id (excluded).
   */
  public static final String DELETE_OLD_COMPLETED =
      "DELETE FROM rca_report_jobs"
          + " WHERE project_id = ? AND interaction_name = ? AND date = ?"
          + " AND status = 'COMPLETED' AND job_id != ?";

  /**
   * Same as {@link #DELETE_OLD_COMPLETED} but for FAILED rows.
   * Params: project_id, interaction_name, date, job_id (excluded).
   */
  public static final String DELETE_OLD_FAILED =
      "DELETE FROM rca_report_jobs"
          + " WHERE project_id = ? AND interaction_name = ? AND date = ?"
          + " AND status = 'FAILED' AND job_id != ?";

  public static final String LIST_STALE_JOBS =
      "SELECT job_id FROM rca_report_jobs"
          + " WHERE status IN ('PENDING', 'PROCESSING')"
          + " AND created_at < DATE_SUB(NOW(), INTERVAL 2 HOUR)";
}
