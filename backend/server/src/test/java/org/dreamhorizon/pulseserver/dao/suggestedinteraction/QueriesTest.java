package org.dreamhorizon.pulseserver.dao.suggestedinteraction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueriesTest {

  @Test
  void shouldDefineSuggestionQueries() {
    assertThat(Queries.GET_SUGGESTIONS_BY_PROJECT).contains("suggested_interaction", "PENDING");
    assertThat(Queries.GET_SUGGESTION_BY_ID).contains("WHERE id = ?", "project_id = ?");
    assertThat(Queries.UPDATE_STATUS).contains("UPDATE suggested_interaction", "decided_at");
    assertThat(Queries.GET_SUGGESTIONS_CATALOG_BY_PROJECT).contains("ORDER BY created_at DESC");
    assertThat(Queries.GET_SUGGESTIONS_CATALOG_BY_PROJECT_AND_STATUS).contains("status = ?");
    assertThat(Queries.DELETE_PENDING_BY_PROJECT).contains("DELETE FROM suggested_interaction");
    assertThat(Queries.INSERT_SUGGESTION).contains("INSERT INTO suggested_interaction");
  }
}
