package org.dreamhorizon.pulseserver.dao.userapikey;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserApiKeyQueriesTest {

  private static final String COLUMNS =
      "id, user_id, display_name, api_key_hash, key_prefix, is_active, created_at, revoked_at, revoked_by";

  @Test
  void shouldExposeInsertQuery() {
    assertThat(UserApiKeyQueries.INSERT).isEqualTo(
        "INSERT INTO user_api_keys (user_id, display_name, api_key_hash, key_prefix, is_active)"
            + " VALUES (?, ?, ?, ?, TRUE)");
  }

  @Test
  void shouldExposeFindActiveByHashQuery() {
    assertThat(UserApiKeyQueries.FIND_ACTIVE_BY_HASH).isEqualTo(
        "SELECT " + COLUMNS + " FROM user_api_keys WHERE api_key_hash = ? AND is_active = TRUE");
  }

  @Test
  void shouldExposeFindActiveByUserQuery() {
    assertThat(UserApiKeyQueries.FIND_ACTIVE_BY_USER).isEqualTo(
        "SELECT " + COLUMNS
            + " FROM user_api_keys WHERE user_id = ? AND is_active = TRUE ORDER BY created_at DESC");
  }

  @Test
  void shouldExposeCountActiveByUserQuery() {
    assertThat(UserApiKeyQueries.COUNT_ACTIVE_BY_USER).isEqualTo(
        "SELECT COUNT(*) AS cnt FROM user_api_keys WHERE user_id = ? AND is_active = TRUE");
  }

  @Test
  void shouldExposeRevokeQuery() {
    assertThat(UserApiKeyQueries.REVOKE).isEqualTo(
        "UPDATE user_api_keys SET is_active = FALSE, revoked_at = CURRENT_TIMESTAMP, revoked_by = ?"
            + " WHERE id = ? AND user_id = ?");
  }
}
