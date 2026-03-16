package org.dreamhorizon.pulseserver.dao.usagelimit;

public class ProjectUsageLimitQueries {

  private static final String LIMIT_COLUMNS =
      "project_usage_limit_id, project_id, usage_limits, is_active, created_at, disabled_at, disabled_by, disabled_reason, created_by";

  private static final String LIMIT_COLUMNS_WITH_NOTIFICATIONS =
      "pul.project_usage_limit_id, pul.project_id, pul.usage_limits, pul.is_active, "
          + "pul.created_at, pul.disabled_at, pul.disabled_by, pul.disabled_reason, pul.created_by, "
          + "COALESCE(uln.thresholds_notified, '{}') as thresholds_notified, "
          + "uln.created_at as notification_created_at";

  private static final String NOTIFICATION_JOIN =
      " LEFT JOIN usage_limit_notifications uln "
          + "ON pul.project_id = uln.project_id "
          + "AND DATE_FORMAT(uln.created_at, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m')";

  public static final String INSERT_USAGE_LIMIT =
      "INSERT INTO project_usage_limits (project_id, usage_limits, is_active, created_by) "
          + "VALUES (?, ?, TRUE, ?)";

  public static final String GET_ACTIVE_LIMIT_BY_PROJECT_ID =
      "SELECT " + LIMIT_COLUMNS_WITH_NOTIFICATIONS
          + " FROM project_usage_limits pul"
          + NOTIFICATION_JOIN
          + " WHERE pul.project_id = ? AND pul.is_active = TRUE";

  public static final String GET_LIMIT_BY_ID =
      "SELECT " + LIMIT_COLUMNS + " FROM project_usage_limits WHERE project_usage_limit_id = ?";

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
          + NOTIFICATION_JOIN
          + " WHERE pul.is_active = TRUE"
          + " ORDER BY pul.project_id";

  public static final String GET_ALL_LIMITS =
      "SELECT " + LIMIT_COLUMNS + " FROM project_usage_limits ORDER BY project_id, created_at DESC";

  public static final String GET_NOTIFICATION_FOR_CURRENT_MONTH =
      "SELECT id, project_id, thresholds_notified, created_at, updated_at "
          + "FROM usage_limit_notifications "
          + "WHERE project_id = ? "
          + "AND DATE_FORMAT(created_at, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m')";

  public static final String INSERT_NOTIFICATION =
      "INSERT INTO usage_limit_notifications (project_id, thresholds_notified) "
          + "VALUES (?, ?)";

  public static final String UPDATE_NOTIFICATION =
      "UPDATE usage_limit_notifications "
          + "SET thresholds_notified = ? "
          + "WHERE id = ?";
}

