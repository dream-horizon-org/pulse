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
    assertThat(config.getDimensionOrder()).contains("Platform");
    assertThat(config.isHybridDimensionOrderingEnabled()).isFalse();
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
