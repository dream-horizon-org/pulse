package org.dreamhorizon.pulseserver.dao.rcajob;

/** SQL for pulse_db.rca_report_jobs (MySQL). */
public final class RcaReportJobQueries {

  private RcaReportJobQueries() {
  }

  /**
   * Insert a new job. Params: job_id, project_id, rca_type, entity_key, date, status,
   * error_message, created_by, worker_instance_id.
   * {@code created_at} / {@code started_at} / {@code completed_at} use DB defaults.
   */
  public static final String INSERT_JOB =
      "INSERT INTO rca_report_jobs (job_id, project_id, rca_type, entity_key, date, status,"
          + " error_message, created_by, worker_instance_id)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

  public static final String GET_JOB_BY_ID =
      "SELECT job_id, project_id, rca_type, entity_key, date, status,"
          + " error_message, created_at, started_at, completed_at, created_by,"
          + " worker_instance_id"
          + " FROM rca_report_jobs WHERE job_id = ?";

  /**
   * Prefer an in-flight job over a queued duplicate.
   * Params: project_id, rca_type, entity_key, date, status1, status2.
   */
  public static final String GET_ACTIVE_JOB_BY_KEY =
      "SELECT job_id, project_id, rca_type, entity_key, date, status,"
          + " error_message, created_at, started_at, completed_at, created_by,"
          + " worker_instance_id"
          + " FROM rca_report_jobs"
          + " WHERE project_id = ? AND rca_type = ? AND entity_key = ? AND date = ?"
          + " AND status IN (?, ?)"
          + " ORDER BY FIELD(status, ?, ?), created_at ASC"
          + " LIMIT 1";

  /**
   * Params: status, status (for IF check), job_id.
   * The second status param is used in the IF expression to check if transitioning to PROCESSING.
   */
  public static final String UPDATE_STATUS =
      "UPDATE rca_report_jobs SET"
          + " status = ?,"
          + " started_at = IF(? = ?, COALESCE(started_at, CURRENT_TIMESTAMP(6)),"
          + " started_at)"
          + " WHERE job_id = ?";

  /** Params: status, job_id. */
  public static final String FINALIZE_SUCCESS =
      "UPDATE rca_report_jobs SET status = ?,"
          + " completed_at = CURRENT_TIMESTAMP(6),"
          + " error_message = NULL"
          + " WHERE job_id = ?";

  /** Params: status, error_message, job_id. */
  public static final String FINALIZE_FAILURE =
      "UPDATE rca_report_jobs SET status = ?,"
          + " error_message = ?,"
          + " completed_at = CURRENT_TIMESTAMP(6)"
          + " WHERE job_id = ?";

  /**
   * Removes any previous jobs with given status for the same logical key.
   * Required because uk_active_job covers all status values, so a prior completed/failed
   * run would otherwise block the UPDATE via a unique-key conflict.
   * Params: project_id, rca_type, entity_key, date, status, job_id (excluded).
   */
  public static final String DELETE_OLD_JOBS =
      "DELETE FROM rca_report_jobs"
          + " WHERE project_id = ? AND rca_type = ? AND entity_key = ? AND date = ?"
          + " AND status = ? AND job_id != ?";

  /**
   * Marks stale PENDING/PROCESSING jobs as FAILED.
   * Params: status (new status), status1, status2 (for IN clause), threshold_minutes (INT).
   */
  public static final String MARK_STALE_JOBS =
      "UPDATE rca_report_jobs SET status = ?,"
          + " error_message = 'Job timed out (stale cleanup)',"
          + " completed_at = CURRENT_TIMESTAMP(6)"
          + " WHERE status IN (?, ?)"
          + " AND created_at < DATE_SUB(NOW(), INTERVAL ? MINUTE)";
}
