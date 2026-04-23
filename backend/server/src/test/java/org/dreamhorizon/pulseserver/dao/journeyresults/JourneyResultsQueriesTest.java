package org.dreamhorizon.pulseserver.dao.journeyresults;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.JourneyResultsQueries;
import org.junit.jupiter.api.Test;

class JourneyResultsQueriesTest {

  @Test
  void shouldIncludeJourneyDirectionAndOrderBy() {
    String sql = JourneyResultsQueries.buildLatestResultsSql("proj-1", 7L, "START");
    assertThat(sql).contains("JourneyId = 7");
    assertThat(sql).contains("ProjectId = 'proj-1'");
    assertThat(sql).contains("Direction = 'START'");
    assertThat(sql).contains("ORDER BY PosFrom");
  }
}
