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
    assertThat(config.getIssueDrillDownCandidateLimit())
        .isEqualTo(RootCauseConfig.DEFAULT_ISSUE_DRILL_DOWN_CANDIDATE_LIMIT);
    assertThat(config.getMinRiskRatioForIssueAttribution())
        .isEqualTo(RootCauseConfig.DEFAULT_MIN_RISK_RATIO_FOR_ISSUE_ATTRIBUTION);
    assertThat(config.getIssueMustPrecedePoor()).isTrue();
    assertThat(config.getDimensionOrder()).contains("Platform");
    assertThat(config.isHybridDimensionOrderingEnabled()).isFalse();
    assertThat(config.getMinCombinedDeltaSignal())
        .isEqualTo(RootCauseConfig.DEFAULT_MIN_COMBINED_DELTA_SIGNAL);
  }

  @Test
  void minCombinedDeltaSignalUnsetNegativeUsesDefault() {
    RootCauseConfig raw = RootCauseConfig.builder().minCombinedDeltaSignal(-1.0d).build();
    assertThat(RootCauseConfig.withDefaults(raw).getMinCombinedDeltaSignal())
        .isEqualTo(RootCauseConfig.DEFAULT_MIN_COMBINED_DELTA_SIGNAL);
  }

  @Test
  void minCombinedDeltaSignalZeroPreservedAsDisabledAtRuntime() {
    RootCauseConfig raw = RootCauseConfig.builder().minCombinedDeltaSignal(0.0d).build();
    assertThat(RootCauseConfig.withDefaults(raw).getMinCombinedDeltaSignal()).isZero();
  }

  @Test
  void minCombinedDeltaSignalCustomPositiveValuePreserved() {
    RootCauseConfig raw = RootCauseConfig.builder().minCombinedDeltaSignal(25.0d).build();
    assertThat(RootCauseConfig.withDefaults(raw).getMinCombinedDeltaSignal()).isEqualTo(25.0d);
  }

  @Test
  void minRiskRatioUnsetNegativeUsesDefault() {
    RootCauseConfig raw =
        RootCauseConfig.builder().minRiskRatioForIssueAttribution(-1.0d).build();
    assertThat(RootCauseConfig.withDefaults(raw).getMinRiskRatioForIssueAttribution())
        .isEqualTo(RootCauseConfig.DEFAULT_MIN_RISK_RATIO_FOR_ISSUE_ATTRIBUTION);
  }

  @Test
  void minRiskRatioZeroPreservedAsDisabledAtRuntime() {
    RootCauseConfig raw = RootCauseConfig.builder().minRiskRatioForIssueAttribution(0.0d).build();
    assertThat(RootCauseConfig.withDefaults(raw).getMinRiskRatioForIssueAttribution()).isZero();
  }

  @Test
  void issueDrillDownCandidateLimitNonPositiveUsesDefault() {
    RootCauseConfig raw = RootCauseConfig.builder().issueDrillDownCandidateLimit(0).build();
    assertThat(RootCauseConfig.withDefaults(raw).getIssueDrillDownCandidateLimit())
        .isEqualTo(RootCauseConfig.DEFAULT_ISSUE_DRILL_DOWN_CANDIDATE_LIMIT);
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

  @Test
  void shouldPreserveHybridDimensionOrderingFlagFromSource() {
    RootCauseConfig partial =
        RootCauseConfig.builder().hybridDimensionOrderingEnabled(true).build();

    RootCauseConfig config = RootCauseConfig.withDefaults(partial);

    assertThat(config.isHybridDimensionOrderingEnabled()).isTrue();
  }
}
