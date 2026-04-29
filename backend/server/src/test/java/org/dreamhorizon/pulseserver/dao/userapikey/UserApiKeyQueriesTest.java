package org.dreamhorizon.pulseserver.dao.userapikey;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserApiKeyQueriesTest {

  @Test
  void shouldExposeSqlConstantsForUserApiKeys() {
    assertThat(UserApiKeyQueries.INSERT).contains("INSERT INTO user_api_keys");
    assertThat(UserApiKeyQueries.FIND_ACTIVE_BY_HASH).contains("api_key_hash");
    assertThat(UserApiKeyQueries.FIND_ACTIVE_BY_USER).contains("user_id");
    assertThat(UserApiKeyQueries.REVOKE).contains("UPDATE user_api_keys");
  }
}
