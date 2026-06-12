package org.dreamhorizon.pulseserver.dao.athena;

/**
 * SQL queries for Athena job operations.
 * Uses project-based isolation — each project has its own Athena table.
 */
public class AthenaJobQueries {
  public static final String CREATE_JOB =
      "INSERT INTO athena_job (job_id, project_id, query_string, user_email, status) " +
      "VALUES (?, ?, ?, ?, 'RUNNING')";

  public static final String UPDATE_JOB_WITH_EXECUTION_ID =
      "UPDATE athena_job SET query_execution_id = ?, status = ?, created_at = ?, updated_at = ? WHERE job_id = ?";

  public static final String UPDATE_JOB_STATUS =
      "UPDATE athena_job SET status = ?, updated_at = ? WHERE job_id = ?";

  public static final String UPDATE_JOB_COMPLETED =
      "UPDATE athena_job SET status = 'COMPLETED', result_location = ?, completed_at = ?, updated_at = ? WHERE job_id = ?";

  public static final String UPDATE_JOB_STATISTICS =
      "UPDATE athena_job SET data_scanned_in_bytes = ?, execution_time_millis = ?, engine_execution_time_millis = ?, query_queue_time_millis = ?, updated_at = ? WHERE job_id = ?";

  public static final String UPDATE_JOB_FAILED =
      "UPDATE athena_job SET status = 'FAILED', error_message = ?, completed_at = ?, updated_at = ? WHERE job_id = ?";

  public static final String GET_JOB_BY_ID =
      "SELECT job_id, project_id, query_string, user_email, query_execution_id, status, result_location, error_message, " +
          "data_scanned_in_bytes, execution_time_millis, engine_execution_time_millis, query_queue_time_millis, " +
          "created_at, updated_at, completed_at " +
          "FROM athena_job WHERE job_id = ?";

  public static final String GET_QUERY_HISTORY =
      "SELECT job_id, project_id, query_string, user_email, query_execution_id, " +
      "status, result_location, error_message, data_scanned_in_bytes, execution_time_millis, " +
      "engine_execution_time_millis, query_queue_time_millis, created_at, updated_at, completed_at " +
      "FROM athena_job WHERE project_id = ? AND user_email = ? " +
      "ORDER BY created_at DESC " +
      "LIMIT ? OFFSET ?";

  public static final String GET_QUERIES_FOR_STATISTICS =
      "SELECT job_id, project_id, query_string, user_email, query_execution_id, " +
      "status, result_location, error_message, data_scanned_in_bytes, execution_time_millis, " +
      "engine_execution_time_millis, query_queue_time_millis, created_at, updated_at, completed_at " +
      "FROM athena_job WHERE project_id = ? AND user_email = ? " +
      "AND created_at >= ? AND created_at <= ? " +
      "ORDER BY created_at DESC";
}
