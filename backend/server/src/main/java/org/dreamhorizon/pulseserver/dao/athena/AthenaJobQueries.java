package org.dreamhorizon.pulseserver.dao.athena;

public class AthenaJobQueries {
  public static final String CREATE_JOB = 
      "INSERT INTO athena_job (job_id, query_string, status) VALUES (?, ?, 'RUNNING')";

  public static final String UPDATE_JOB_WITH_EXECUTION_ID = 
      "UPDATE athena_job SET query_execution_id = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE job_id = ?";

  public static final String UPDATE_JOB_STATUS = 
      "UPDATE athena_job SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE job_id = ?";

  public static final String UPDATE_JOB_COMPLETED = 
      "UPDATE athena_job SET status = 'COMPLETED', result_location = ?, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE job_id = ?";

  public static final String UPDATE_JOB_FAILED = 
      "UPDATE athena_job SET status = 'FAILED', error_message = ?, updated_at = CURRENT_TIMESTAMP WHERE job_id = ?";

  public static final String GET_JOB_BY_ID = 
      "SELECT job_id, query_string, query_execution_id, status, result_location, error_message, " +
      "created_at, updated_at, completed_at " +
      "FROM athena_job WHERE job_id = ?";
}



