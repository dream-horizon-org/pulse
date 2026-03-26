package org.dreamhorizon.pulseserver.dao.usagelimit;

public class ProjectUsageLimitQueries {

  private static final String LIMIT_COLUMNS =
      "project_usage_limit_id, project_id, usage_limits, is_active, created_at, disabled_at, disabled_by, disabled_reason, created_by";

  public static final String INSERT_USAGE_LIMIT =
      "INSERT INTO project_usage_limits (project_id, usage_limits, is_active, created_by) "
          + "VALUES (?, ?, TRUE, ?)";

  public static final String GET_ACTIVE_LIMIT_BY_PROJECT_ID =
      "SELECT " + LIMIT_COLUMNS + " FROM project_usage_limits WHERE project_id = ? AND is_active = TRUE";

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
      "SELECT " + LIMIT_COLUMNS + " FROM project_usage_limits WHERE is_active = TRUE ORDER BY project_id";

  public static final String GET_ALL_LIMITS =
      "SELECT " + LIMIT_COLUMNS + " FROM project_usage_limits ORDER BY project_id, created_at DESC";
}

