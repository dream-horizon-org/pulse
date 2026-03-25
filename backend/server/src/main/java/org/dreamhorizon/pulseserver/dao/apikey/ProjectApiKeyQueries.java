package org.dreamhorizon.pulseserver.dao.apikey;

public class ProjectApiKeyQueries {

  private static final String API_KEY_COLUMNS =
      "project_api_key_id, project_id, display_name, api_key_encrypted, encryption_salt, api_key_digest, "
          + "is_active, expires_at, grace_period_ends_at, created_by, created_at, "
          + "deactivated_at, deactivated_by, deactivation_reason";

  public static final String INSERT_API_KEY =
      "INSERT INTO project_api_keys (project_id, display_name, api_key_encrypted, encryption_salt, api_key_digest, is_active, expires_at, created_by) "
          + "VALUES (?, ?, ?, ?, ?, TRUE, ?, ?)";

  public static final String GET_API_KEY_BY_ID =
      "SELECT " + API_KEY_COLUMNS + " FROM project_api_keys WHERE project_api_key_id = ?";

  public static final String GET_ACTIVE_API_KEYS_BY_PROJECT_ID =
      "SELECT " + API_KEY_COLUMNS + " FROM project_api_keys WHERE project_id = ? AND is_active = TRUE ORDER BY created_at DESC";

  public static final String GET_API_KEY_BY_DIGEST =
      "SELECT " + API_KEY_COLUMNS + " FROM project_api_keys WHERE api_key_digest = ?";

  public static final String GET_ALL_API_KEYS_BY_PROJECT_ID =
      "SELECT " + API_KEY_COLUMNS + " FROM project_api_keys WHERE project_id = ? ORDER BY created_at DESC";

  public static final String DEACTIVATE_API_KEY =
      "UPDATE project_api_keys SET is_active = FALSE, deactivated_at = CURRENT_TIMESTAMP, "
          + "deactivated_by = ?, deactivation_reason = ?, grace_period_ends_at = ? "
          + "WHERE project_api_key_id = ? AND project_id = ?";

  public static final String CHECK_ACTIVE_API_KEY_EXISTS =
      "SELECT COUNT(*) as count FROM project_api_keys WHERE project_id = ? AND is_active = TRUE";

  /**
   * Valid keys are either:
   * 1. Active (is_active = TRUE), OR
   * 2. Inactive but still in grace period (is_active = FALSE AND grace_period_ends_at > NOW())
   * 
   * Also excludes expired keys (expires_at IS NOT NULL AND expires_at <= NOW())
   */
  public static final String GET_ALL_VALID_API_KEYS =
      "SELECT " + API_KEY_COLUMNS + " FROM project_api_keys "
          + "WHERE (is_active = TRUE OR (is_active = FALSE AND grace_period_ends_at > CURRENT_TIMESTAMP)) "
          + "AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)";

  public static final String GET_VALID_API_KEYS_BY_PROJECT_ID =
      "SELECT " + API_KEY_COLUMNS + " FROM project_api_keys "
          + "WHERE project_id = ? AND (is_active = TRUE OR (is_active = FALSE AND grace_period_ends_at > CURRENT_TIMESTAMP)) "
          + "AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)";
}

