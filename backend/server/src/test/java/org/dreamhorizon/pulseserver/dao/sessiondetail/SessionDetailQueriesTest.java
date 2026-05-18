package org.dreamhorizon.pulseserver.dao.sessiondetail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionDetailQueriesTest {

  @Test
  void sessionInteractionsQueryUsesMaterializedApdex() {
    assertThat(SessionDetailQueries.GET_SESSION_INTERACTIONS)
        .contains("round(avg(nullIf(ApdexScore, 0)), 2)")
        .doesNotContain("toFloat64OrNull(\n          SpanAttributes['pulse.interaction.apdex_score']\n        )");
  }

  @Test
  void sessionCoreQueryUsesMaterializedApdex() {
    assertThat(SessionDetailQueries.GET_SESSION_CORE)
        .contains("round(avg(nullIf(ApdexScore, 0)), 2)")
        .doesNotContain("avgIf(\n            toFloat64OrNull(\n              SpanAttributes['pulse.interaction.apdex_score']");
  }
}
