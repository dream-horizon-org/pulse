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
   * Stops auto-refresh by setting {@code expiry = NOW()}. Leaves {@code funnel_type = 'AUTO'}
   * untouched so the listing keeps showing "AUTO" — only the computed status changes.
   *
   * <p>Two effects:
   * <ul>
   *   <li>{@code SELECT_ALL_AUTO} filters out rows where {@code expiry <= NOW()}, so the
   *       cron stops picking it up.</li>
   *   <li>{@code FUNNEL_COMPUTED_STATUS_CASE} has an "AUTO + expired" branch that maps to
   *       {@code COMPLETED}, so the listing's status badge flips immediately.</li>
   * </ul>
   *
   * <p>WHERE clause guards: only update rows currently AUTO and not already expired, owned
   * by the caller's project. Re-applying on an already-stopped funnel is a no-op (rowCount=0)
   * which the service treats as idempotent success.
   */
  public static final String STOP_AUTO =
    "UPDATE funnel SET expiry = CURRENT_TIMESTAMP,\n"
      + "  updated_at = CURRENT_TIMESTAMP\n"
      + "WHERE project_id = ? AND id = ?\n"
      + "  AND funnel_type = 'AUTO'\n"
      + "  AND (expiry IS NULL OR expiry > CURRENT_TIMESTAMP)";

  /**
   * Latest job status for FUNNEL runs; subquery must be correlated with alias {@code funnel} (table name).
   */
  public static final String LATEST_FUNNEL_JOB_STATUS =
    "(SELECT sj.status FROM analytics_jobs sj WHERE sj.job_type = 'FUNNEL' AND sj.reference_id = funnel.id "
      + "ORDER BY sj.id DESC LIMIT 1)";

  public static final String SELECT_BY_ID =
    """
      SELECT funnel.id, funnel.project_id, funnel.name, funnel.description, funnel.funnel_type, funnel.step_order_type,
          funnel.steps_json, funnel.window_seconds, funnel.mode, funnel.filters_json, funnel.date_range,
          funnel.start_time, funnel.end_time, funnel.expiry, funnel.created_at, funnel.updated_at,
          funnel.created_by,
      """
      + LATEST_FUNNEL_JOB_STATUS
      + " AS latest_job_status "
      + "FROM funnel WHERE funnel.id = ?";

  /**
   * Cron picks up only AUTO funnels whose {@code expiry} is null or still in the future.
   * Setting {@code expiry = NOW()} via {@link #STOP_AUTO} excludes a funnel from the next
   * batch run without changing its {@code funnel_type}.
   */
  public static final String SELECT_ALL_AUTO =
    """
      SELECT funnel.id, funnel.project_id, funnel.name, funnel.description, funnel.funnel_type, funnel.step_order_type,
          funnel.steps_json, funnel.window_seconds, funnel.mode, funnel.filters_json, funnel.date_range,
          funnel.start_time, funnel.end_time, funnel.expiry, funnel.created_at, funnel.updated_at,
          funnel.created_by, NULL AS latest_job_status
      FROM funnel
      WHERE funnel.funnel_type = 'AUTO'
        AND (funnel.expiry IS NULL OR funnel.expiry > CURRENT_TIMESTAMP)
      """;

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
      // IN_PROGRESS first — even if the funnel was just stopped, an in-flight job should
      // be reflected so the user sees it finish.
      + "WHEN "
      + LATEST_FUNNEL_JOB_STATUS
      + " IN ('PENDING', 'RUNNING') THEN 'IN_PROGRESS' "
      // AUTO funnels whose expiry has passed are stopped. Stays AUTO in funnel_type but
      // reads as COMPLETED in the listing's status column. Set by the Mark-as-Completed
      // action (STOP_AUTO sets expiry = NOW()).
      + "WHEN funnel.funnel_type = 'AUTO' AND funnel.expiry IS NOT NULL "
      + "AND funnel.expiry <= CURRENT_TIMESTAMP THEN 'COMPLETED' "
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
