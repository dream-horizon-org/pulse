package org.dreamhorizon.pulseserver.dao.productAnalysis.journey;

public final class JourneyQueries {

  public static final String INSERT =
    """
      INSERT INTO journey (project_id, name, description, anchor_event, direction, depth, mode, filters_json,
          start_time, end_time, journey_type, expiry, date_range, created_by)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  public static final String UPDATE =
    """
      UPDATE journey SET name = ?, description = ?, anchor_event = ?, direction = ?, depth = ?, mode = ?,
          filters_json = ?, start_time = ?, end_time = ?, journey_type = ?, expiry = ?, date_range = ?,
          updated_at = CURRENT_TIMESTAMP
      WHERE project_id = ? AND id = ?
      """;

  public static final String DELETE = "DELETE FROM journey WHERE project_id = ? AND id = ?";

  /**
   * Stops auto-refresh by setting {@code expiry = NOW()}. Leaves {@code journey_type = 'AUTO'}
   * untouched so the listing keeps showing "AUTO" — only the computed status flips to
   * COMPLETED via {@link #JOURNEY_COMPUTED_STATUS_CASE}'s "AUTO + expired" branch.
   *
   * <p>Mirrors {@code FunnelDefinitionQueries.STOP_AUTO}.
   */
  public static final String STOP_AUTO =
    "UPDATE journey SET expiry = CURRENT_TIMESTAMP,\n"
      + "  updated_at = CURRENT_TIMESTAMP\n"
      + "WHERE project_id = ? AND id = ?\n"
      + "  AND journey_type = 'AUTO'\n"
      + "  AND (expiry IS NULL OR expiry > CURRENT_TIMESTAMP)";

  public static final String LATEST_JOURNEY_JOB_STATUS =
    "(SELECT sj.status FROM analytics_jobs sj WHERE sj.job_type = 'JOURNEY' AND sj.reference_id = journey.id "
      + "ORDER BY sj.id DESC LIMIT 1)";

  public static final String SELECT_BY_ID =
    """
      SELECT journey.id, journey.project_id, journey.name, journey.description, journey.anchor_event, journey.direction,
          journey.depth, journey.mode, journey.filters_json, journey.start_time, journey.end_time,
          journey.journey_type, journey.expiry, journey.date_range, journey.created_at, journey.updated_at,
          journey.created_by,
      """
      + LATEST_JOURNEY_JOB_STATUS
      + " AS latest_job_status "
      + "FROM journey WHERE journey.id = ?";

  /**
   * Cron picks up only AUTO journeys whose {@code expiry} is null or still in the future.
   * Setting {@code expiry = NOW()} via {@link #STOP_AUTO} excludes a journey from the next
   * batch run without changing its {@code journey_type}.
   */
  public static final String SELECT_ALL_AUTO =
    """
      SELECT journey.id, journey.project_id, journey.name, journey.description, journey.anchor_event, journey.direction,
          journey.depth, journey.mode, journey.filters_json, journey.start_time, journey.end_time,
          journey.journey_type, journey.expiry, journey.date_range, journey.created_at, journey.updated_at,
          journey.created_by, NULL AS latest_job_status
      FROM journey
      WHERE journey.journey_type = 'AUTO'
        AND (journey.expiry IS NULL OR journey.expiry > CURRENT_TIMESTAMP)
      """;

  public static final String SELECT_BY_PROJECT_AND_ID =
    """
      SELECT journey.id, journey.project_id, journey.name, journey.description, journey.anchor_event, journey.direction,
          journey.depth, journey.mode, journey.filters_json, journey.start_time, journey.end_time,
          journey.journey_type, journey.expiry, journey.date_range, journey.created_at, journey.updated_at,
          journey.created_by,
      """
      + LATEST_JOURNEY_JOB_STATUS
      + " AS latest_job_status "
      + "FROM journey WHERE journey.project_id = ? AND journey.id = ?";

  public static final String JOURNEY_COMPUTED_STATUS_CASE =
    "CASE "
      // IN_PROGRESS first — even if the journey was just stopped, an in-flight job should
      // be reflected so the user sees it finish.
      + "WHEN "
      + LATEST_JOURNEY_JOB_STATUS
      + " IN ('PENDING', 'RUNNING') THEN 'IN_PROGRESS' "
      // AUTO journeys whose expiry has passed are stopped. Stays AUTO in journey_type but
      // reads as COMPLETED in the listing's status column. Set by the Mark-as-Completed
      // action (STOP_AUTO sets expiry = NOW()).
      + "WHEN journey.journey_type = 'AUTO' AND journey.expiry IS NOT NULL "
      + "AND journey.expiry <= CURRENT_TIMESTAMP THEN 'COMPLETED' "
      + "WHEN journey.journey_type = 'ONCE' AND "
      + LATEST_JOURNEY_JOB_STATUS
      + " IS NULL THEN 'PENDING' "
      + "WHEN journey.journey_type = 'AUTO' AND "
      + LATEST_JOURNEY_JOB_STATUS
      + " IS NULL THEN 'ACTIVE' "
      + "WHEN journey.journey_type = 'AUTO' AND "
      + LATEST_JOURNEY_JOB_STATUS
      + " = 'FAILED' THEN 'WARN' "
      + "WHEN journey.journey_type = 'ONCE' AND "
      + LATEST_JOURNEY_JOB_STATUS
      + " = 'FAILED' THEN 'FAILED' "
      + "WHEN journey.journey_type = 'ONCE' AND "
      + LATEST_JOURNEY_JOB_STATUS
      + " = 'SUCCEEDED' THEN 'COMPLETED' "
      + "WHEN journey.journey_type = 'AUTO' AND "
      + LATEST_JOURNEY_JOB_STATUS
      + " = 'SUCCEEDED' THEN 'ACTIVE' "
      + "ELSE 'ACTIVE' END";

  /** Distinct non-null creator emails/names for all journeys in a project. */
  public static final String SELECT_DISTINCT_CREATED_BY =
    "SELECT DISTINCT created_by FROM journey WHERE project_id = ? "
      + "AND created_by IS NOT NULL AND created_by != '' "
      + "ORDER BY created_by";

  private JourneyQueries() {
  }
}
