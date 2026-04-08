package org.dreamhorizon.pulseserver.dao.suggestedinteraction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueriesTest {

  @Test
  void shouldDefineSuggestionQueries() {
    assertThat(Queries.GET_SUGGESTIONS_BY_PROJECT).contains("suggested_interaction", "PENDING");
    assertThat(Queries.GET_SUGGESTION_BY_ID).contains("WHERE id = ?", "project_id = ?");
    assertThat(Queries.UPDATE_STATUS).contains("UPDATE suggested_interaction", "dismissed_at");
  }
}
