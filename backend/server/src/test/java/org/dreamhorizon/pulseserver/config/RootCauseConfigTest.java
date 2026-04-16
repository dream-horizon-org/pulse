package org.dreamhorizon.pulseserver.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RootCauseConfigTest {

  @Test
  void shouldApplyDefaultsWhenSourceIsNull() {
    RootCauseConfig config = RootCauseConfig.withDefaults(null);

    assertThat(config.getSimilarityThresholdPct()).isEqualTo(75);
    assertThat(config.getLookbackDays()).isEqualTo(7);
    assertThat(config.getMaxSegments()).isEqualTo(4);
    assertThat(config.getMinPoorSessionsForErrorAttribution())
        .isEqualTo(RootCauseConfig.DEFAULT_MIN_POOR_SESSIONS_FOR_ERROR_ATTRIBUTION);
    assertThat(config.getMinTreatedSessionsForIssueAttribution())
        .isEqualTo(RootCauseConfig.DEFAULT_MIN_TREATED_SESSIONS_FOR_ISSUE_ATTRIBUTION);
    assertThat(config.getMinControlSessionsForIssueAttribution())
        .isEqualTo(RootCauseConfig.DEFAULT_MIN_CONTROL_SESSIONS_FOR_ISSUE_ATTRIBUTION);
    assertThat(config.getIssueDrillDownLimit()).isEqualTo(RootCauseConfig.DEFAULT_ISSUE_DRILL_DOWN_LIMIT);
    assertThat(config.getDimensionOrder()).contains("Platform");
  }

  @Test
  void shouldReplaceNonPositiveValuesWithDefaults() {
    RootCauseConfig partial =
        RootCauseConfig.builder()
            .similarityThresholdPct(0)
            .lookbackDays(-1)
            .maxSegments(0)
            .dimensionOrder(List.of())
            .build();

    RootCauseConfig config = RootCauseConfig.withDefaults(partial);

    assertThat(config.getSimilarityThresholdPct()).isEqualTo(75);
    assertThat(config.getLookbackDays()).isEqualTo(7);
    assertThat(config.getMaxSegments()).isEqualTo(4);
    assertThat(config.getDimensionOrder()).isNotEmpty();
  }
}
