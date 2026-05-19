package org.dreamhorizon.pulseserver.dao.insightjob;

public final class InsightJobQueries {

  private InsightJobQueries() {}

  public static final String INSERT_JOB =
      "INSERT INTO insight_jobs (job_id, project_id, insight_type, entity_key, execution_mode,"
          + " start_date, end_date, status, error_message, created_by)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  public static final String GET_JOB_BY_ID =
      "SELECT job_id, project_id, insight_type, entity_key, execution_mode,"
          + " start_date, end_date, status, error_message,"
          + " created_at, started_at, completed_at, created_by"
          + " FROM insight_jobs WHERE job_id = ?";

  public static final String GET_ACTIVE_JOB_BY_KEY =
      "SELECT job_id, project_id, insight_type, entity_key, execution_mode,"
          + " start_date, end_date, status, error_message,"
          + " created_at, started_at, completed_at, created_by"
          + " FROM insight_jobs"
          + " WHERE project_id = ? AND insight_type = ? AND entity_key = ?"
          + " AND execution_mode = ?"
          + " AND COALESCE(start_date, '1970-01-01') = COALESCE(?, '1970-01-01')"
          + " AND COALESCE(end_date, '1970-01-01') = COALESCE(?, '1970-01-01')"
          + " AND status IN (?, ?)"
          + " ORDER BY FIELD(status, ?, ?), created_at ASC"
          + " LIMIT 1";

  public static final String UPDATE_STATUS =
      "UPDATE insight_jobs SET"
          + " status = ?,"
          + " started_at = IF(? = ?, COALESCE(started_at, CURRENT_TIMESTAMP(6)), started_at)"
          + " WHERE job_id = ?";

  public static final String FINALIZE_SUCCESS =
      "UPDATE insight_jobs SET status = ?,"
          + " completed_at = CURRENT_TIMESTAMP(6),"
          + " error_message = NULL"
          + " WHERE job_id = ?";

  public static final String FINALIZE_FAILURE =
      "UPDATE insight_jobs SET status = ?,"
          + " error_message = ?,"
          + " completed_at = CURRENT_TIMESTAMP(6)"
          + " WHERE job_id = ?";

  public static final String DELETE_OLD_JOBS =
      "DELETE FROM insight_jobs"
          + " WHERE project_id = ? AND insight_type = ? AND entity_key = ?"
          + " AND execution_mode = ?"
          + " AND COALESCE(start_date, '1970-01-01') = COALESCE(?, '1970-01-01')"
          + " AND COALESCE(end_date, '1970-01-01') = COALESCE(?, '1970-01-01')"
          + " AND status = ? AND job_id != ?";

  public static final String MARK_STALE_JOBS =
      "UPDATE insight_jobs SET status = ?,"
          + " error_message = 'Job timed out (stale cleanup)',"
          + " completed_at = CURRENT_TIMESTAMP(6)"
          + " WHERE status IN (?, ?)"
          + " AND created_at < DATE_SUB(NOW(), INTERVAL ? MINUTE)";
}
