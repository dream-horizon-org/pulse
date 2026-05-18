package org.dreamhorizon.pulseserver.dao.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionListingConstantsTest {

  @Test
  void impactedInteractionsSelectFiltersViaMaterializedColumns() {
    assertThat(SessionListingConstants.IMPACTED_INTERACTIONS_SELECT)
        .contains("UserCategory = 'Poor'")
        .contains("FrozenFrameCount > 0")
        .doesNotContain("ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor'")
        .doesNotContain("toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count'])");
  }
}
