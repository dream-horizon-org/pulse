package org.dreamhorizon.pulseserver.dao.userapikey;

public class UserApiKeyQueries {

  private static final String COLUMNS =
      "id, user_id, display_name, api_key_hash, key_prefix, is_active, created_at, revoked_at, revoked_by";

  public static final String INSERT =
      "INSERT INTO user_api_keys (user_id, display_name, api_key_hash, key_prefix, is_active)"
          + " VALUES (?, ?, ?, ?, TRUE)";

  public static final String FIND_ACTIVE_BY_HASH =
      "SELECT " + COLUMNS + " FROM user_api_keys WHERE api_key_hash = ? AND is_active = TRUE";

  public static final String FIND_ACTIVE_BY_USER =
      "SELECT " + COLUMNS + " FROM user_api_keys WHERE user_id = ? AND is_active = TRUE ORDER BY created_at DESC";

  public static final String REVOKE =
      "UPDATE user_api_keys SET is_active = FALSE, revoked_at = CURRENT_TIMESTAMP, revoked_by = ?"
          + " WHERE id = ? AND user_id = ?";
}
