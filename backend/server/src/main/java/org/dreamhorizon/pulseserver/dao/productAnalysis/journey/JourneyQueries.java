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
   * Bumps {@code updated_at} without changing any other field. Used by the AUTO batch cron after
   * a successful ClickHouse compute so the listing's "Last updated" reflects the latest run.
   */
  public static final String TOUCH_UPDATED_AT =
    "UPDATE journey SET updated_at = CURRENT_TIMESTAMP WHERE id = ?";

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

  public static final String SELECT_ALL_AUTO =
    """
      SELECT journey.id, journey.project_id, journey.name, journey.description, journey.anchor_event, journey.direction,
          journey.depth, journey.mode, journey.filters_json, journey.start_time, journey.end_time,
          journey.journey_type, journey.expiry, journey.date_range, journey.created_at, journey.updated_at,
          journey.created_by, NULL AS latest_job_status
      FROM journey WHERE journey.journey_type = 'AUTO'
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
      + "WHEN "
      + LATEST_JOURNEY_JOB_STATUS
      + " IN ('PENDING', 'RUNNING') THEN 'IN_PROGRESS' "
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
