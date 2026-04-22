package org.dreamhorizon.pulseserver.dao.usagelimit;

public class ProjectUsageLimitQueries {

  private static final String LIMIT_COLUMNS =
      "project_usage_limit_id, project_id, usage_limits, is_active, created_at, disabled_at, disabled_by, disabled_reason, created_by";

  private static final String LIMIT_COLUMNS_WITH_NOTIFICATIONS =
      "pul.project_usage_limit_id, pul.project_id, pul.usage_limits, pul.is_active, "
          + "pul.created_at, pul.disabled_at, pul.disabled_by, pul.disabled_reason, pul.created_by, "
          + "uln.thresholds_notified AS thresholds_notified, "
          + "uln.project_usage_limit_id AS notification_project_usage_limit_id, "
          + "uln.is_active AS notification_row_active, "
          + "uln.created_at AS notification_created_at, "
          + "COALESCE(p.name, pul.project_id) as project_name, "
          + "p.tenant_id as tenant_id";

  private static final String NOTIFICATION_JOIN =
      " LEFT JOIN usage_limit_notifications uln "
          + "ON pul.project_id = uln.project_id "
          + "AND DATE_FORMAT(uln.created_at, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m') "
          + "AND uln.is_active = TRUE";

  private static final String PROJECT_JOIN =
      " LEFT JOIN projects p ON pul.project_id = p.project_id";

  public static final String INSERT_USAGE_LIMIT =
      "INSERT INTO project_usage_limits (project_id, usage_limits, is_active, created_by) "
          + "VALUES (?, ?, TRUE, ?)";

  public static final String GET_ACTIVE_LIMIT_BY_PROJECT_ID =
      "SELECT " + LIMIT_COLUMNS_WITH_NOTIFICATIONS
          + " FROM project_usage_limits pul"
          + PROJECT_JOIN
          + NOTIFICATION_JOIN
          + " WHERE pul.project_id = ? AND pul.is_active = TRUE";

  public static final String GET_LIMIT_BY_ID =
      "SELECT " + LIMIT_COLUMNS + " FROM project_usage_limits WHERE project_usage_limit_id = ?";

  /** Marks all active notification rows for a project inactive (e.g. when usage limits are replaced). */
  public static final String DEACTIVATE_ACTIVE_NOTIFICATIONS_FOR_PROJECT =
      "UPDATE usage_limit_notifications SET is_active = FALSE "
          + "WHERE project_id = ? AND is_active = TRUE";

  public static final String GET_ALL_LIMITS_BY_PROJECT_ID =
      "SELECT " + LIMIT_COLUMNS + " FROM project_usage_limits WHERE project_id = ? ORDER BY created_at DESC";

  public static final String SOFT_DELETE_ACTIVE_LIMIT =
      "UPDATE project_usage_limits SET is_active = FALSE, disabled_at = CURRENT_TIMESTAMP, "
          + "disabled_by = ?, disabled_reason = ? WHERE project_id = ? AND is_active = TRUE";

  public static final String SOFT_DELETE_ACTIVE_LIMITS_FOR_PROJECTS =
      "UPDATE project_usage_limits SET is_active = FALSE, disabled_at = CURRENT_TIMESTAMP, "
          + "disabled_by = ?, disabled_reason = ? WHERE project_id IN (%s) AND is_active = TRUE";

  public static final String CHECK_ACTIVE_LIMIT_EXISTS =
      "SELECT COUNT(*) as count FROM project_usage_limits WHERE project_id = ? AND is_active = TRUE";

  public static final String GET_LIMIT_HISTORY_BY_PROJECT_ID =
      "SELECT " + LIMIT_COLUMNS + " FROM project_usage_limits WHERE project_id = ? AND is_active = FALSE ORDER BY disabled_at DESC";

  public static final String GET_ALL_ACTIVE_LIMITS =
      "SELECT " + LIMIT_COLUMNS_WITH_NOTIFICATIONS
          + " FROM project_usage_limits pul"
          + PROJECT_JOIN
          + NOTIFICATION_JOIN
          + " WHERE pul.is_active = TRUE"
          + " ORDER BY pul.project_id";

  public static final String GET_ALL_LIMITS =
      "SELECT " + LIMIT_COLUMNS + " FROM project_usage_limits ORDER BY project_id, created_at DESC";

  public static final String GET_NOTIFICATION_FOR_CURRENT_MONTH =
      "SELECT uln.id, uln.project_id, uln.thresholds_notified, uln.project_usage_limit_id, "
          + "uln.is_active, uln.created_at, uln.updated_at "
          + "FROM usage_limit_notifications uln "
          + "WHERE uln.project_id = ? "
          + "AND DATE_FORMAT(uln.created_at, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m') "
          + "AND uln.is_active = TRUE";

  public static final String INSERT_NOTIFICATION =
      "INSERT INTO usage_limit_notifications (project_id, thresholds_notified, project_usage_limit_id, is_active) "
          + "VALUES (?, ?, ?, TRUE)";

  public static final String UPDATE_NOTIFICATION =
      "UPDATE usage_limit_notifications "
          + "SET thresholds_notified = ? "
          + "WHERE id = ?";

  /**
   * ClickHouse ({ otel} database) — monthly rollup per project for usage-limit notifications.
   */
  public static final String CLICKHOUSE_GET_CURRENT_MONTH_USAGE_BY_PROJECT = """
      SELECT
          project_id,
          sum(event_count) AS events_used,
          uniqCombined64Merge(session_count) AS sessions_used
      FROM otel.project_monthly_usage
      WHERE month = toStartOfMonth(now())
      GROUP BY project_id
      """;
}

