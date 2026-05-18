package org.dreamhorizon.pulseserver.dao.heatmap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HeatmapQueriesTest {

  @Test
  void interactionsApdexQueryUsesMaterializedApdex() {
    assertThat(HeatmapQueries.INTERACTIONS_APDEX_FOR_INTERACTION_NAMES)
        .contains("round(avg(nullIf(ApdexScore, 0)), 2)")
        .doesNotContain("toFloat64OrNull(SpanAttributes['pulse.interaction.apdex_score'])");
  }
}
