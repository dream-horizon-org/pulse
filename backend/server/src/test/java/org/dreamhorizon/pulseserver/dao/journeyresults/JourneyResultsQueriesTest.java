package org.dreamhorizon.pulseserver.dao.journeyresults;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JourneyResultsQueriesTest {

  @Test
  void shouldIncludeJourneyDirectionAndOrderBy() {
    String sql = JourneyResultsQueries.buildLatestResultsSql("proj-1", 7L, "START");
    assertThat(sql).contains("journey_id = '7'");
    assertThat(sql).contains("project_id = 'proj-1'");
    assertThat(sql).contains("direction = 'START'");
    assertThat(sql).contains("ORDER BY pos_from");
  }
}
