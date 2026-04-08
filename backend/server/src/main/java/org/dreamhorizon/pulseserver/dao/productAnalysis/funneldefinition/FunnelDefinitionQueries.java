package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition;

public final class FunnelDefinitionQueries {

  public static final String INSERT =
    """
      INSERT INTO funnel (project_id, name, description, funnel_type, step_order_type, steps_json,
          window_seconds, mode, filters_json, date_range, start_time, end_time, expiry, created_by)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  public static final String UPDATE =
    """
      UPDATE funnel SET name = ?, description = ?, funnel_type = ?, step_order_type = ?, steps_json = ?,
          window_seconds = ?, mode = ?, filters_json = ?, date_range = ?, start_time = ?, end_time = ?,
          expiry = ?, updated_at = CURRENT_TIMESTAMP
      WHERE project_id = ? AND id = ?
      """;

  public static final String DELETE = "DELETE FROM funnel WHERE project_id = ? AND id = ?";

  /**
   * Latest job status for FUNNEL runs; subquery must be correlated with alias {@code funnel} (table name).
   */
  public static final String LATEST_FUNNEL_JOB_STATUS =
    "(SELECT sj.status FROM spark_jobs sj WHERE sj.job_type = 'FUNNEL' AND sj.reference_id = funnel.id "
      + "ORDER BY sj.id DESC LIMIT 1)";

  public static final String SELECT_BY_PROJECT_AND_ID =
    """
      SELECT funnel.id, funnel.project_id, funnel.name, funnel.description, funnel.funnel_type, funnel.step_order_type,
          funnel.steps_json, funnel.window_seconds, funnel.mode, funnel.filters_json, funnel.date_range,
          funnel.start_time, funnel.end_time, funnel.expiry, funnel.created_at, funnel.updated_at,
          funnel.created_by,
      """
      + LATEST_FUNNEL_JOB_STATUS
      + " AS latest_job_status "
      + "FROM funnel WHERE funnel.project_id = ? AND funnel.id = ?";

  /**
   * SQL expression for computed status (same order as {@link
   * org.dreamhorizon.pulseserver.analysis.AnalysisComputedStatusResolver}). Uses table name {@code funnel}.
   */
  public static final String FUNNEL_COMPUTED_STATUS_CASE =
    "CASE "
      + "WHEN "
      + LATEST_FUNNEL_JOB_STATUS
      + " IN ('PENDING', 'RUNNING') THEN 'IN_PROGRESS' "
      + "WHEN funnel.funnel_type = 'ONCE' AND "
      + LATEST_FUNNEL_JOB_STATUS
      + " IS NULL THEN 'PENDING' "
      + "WHEN funnel.funnel_type = 'AUTO' AND "
      + LATEST_FUNNEL_JOB_STATUS
      + " IS NULL THEN 'ACTIVE' "
      + "WHEN funnel.funnel_type = 'AUTO' AND "
      + LATEST_FUNNEL_JOB_STATUS
      + " = 'FAILED' THEN 'WARN' "
      + "WHEN funnel.funnel_type = 'ONCE' AND "
      + LATEST_FUNNEL_JOB_STATUS
      + " = 'FAILED' THEN 'FAILED' "
      + "WHEN funnel.funnel_type = 'ONCE' AND "
      + LATEST_FUNNEL_JOB_STATUS
      + " = 'SUCCEEDED' THEN 'COMPLETED' "
      + "WHEN funnel.funnel_type = 'AUTO' AND "
      + LATEST_FUNNEL_JOB_STATUS
      + " = 'SUCCEEDED' THEN 'ACTIVE' "
      + "ELSE 'ACTIVE' END";

  /** Distinct non-null creator emails/names for all funnels in a project. */
  public static final String SELECT_DISTINCT_CREATED_BY =
    "SELECT DISTINCT created_by FROM funnel WHERE project_id = ? "
      + "AND created_by IS NOT NULL AND created_by != '' "
      + "ORDER BY created_by";

  private FunnelDefinitionQueries() {
  }
}
