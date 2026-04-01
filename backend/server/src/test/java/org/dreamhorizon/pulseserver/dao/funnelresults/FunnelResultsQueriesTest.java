package org.dreamhorizon.pulseserver.dao.funnelresults;

import static org.assertj.core.api.Assertions.assertThat;

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
    assertThat(sql).contains("funnel_id = '42'");
    assertThat(sql).contains("project_id = 'proj-1'");
    assertThat(sql).contains("ORDER BY step_index ASC");
  }
}
