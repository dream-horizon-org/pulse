package org.dreamhorizon.pulseserver.dao.funnelresults;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.FunnelResultsQueries;
import org.junit.jupiter.api.Test;

class FunnelResultsQueriesTest {

  @Test
  void shouldEscapeQuotesInProjectIdForSqlLiteral() {
    assertThat(FunnelResultsQueries.escapeChStringLiteral("p'j"))
      .isEqualTo("p''j");
  }

  @Test
  void shouldBuildSqlWithFunnelAndProjectIds() {
    String sql = FunnelResultsQueries.buildLatestResultsSql("proj-1", 42L);
    assertThat(sql).contains("FunnelId = 42");
    assertThat(sql).contains("ProjectId = 'proj-1'");
    assertThat(sql).contains("ORDER BY StepIndex ASC");
  }
}
