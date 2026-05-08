package org.dreamhorizon.pulseserver.service.rootcause;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.junit.jupiter.api.Test;

class RcaHybridMergeOutcomeTest {

  @Test
  void shouldUseFlatModeWhenHierarchicalTierIsEmpty() {
    assertThat(RcaHybridMergeOutcome.modeFromHierarchicalTier(List.of()))
        .isEqualTo(RootCauseAnalysisMode.FLAT);
  }

  @Test
  void shouldUseHybridModeWhenHierarchicalTierNonEmpty() {
    RootCauseSegment twoDim =
        RootCauseSegment.builder()
            .label("a + b")
            .dimensions(new LinkedHashMap<>(Map.of("platform", "ios", "app.version", "1.0")))
            .metrics(Map.of("volume", 10L, "problematic_count", 2L))
            .build();
    assertThat(RcaHybridMergeOutcome.modeFromHierarchicalTier(List.of(twoDim)))
        .isEqualTo(RootCauseAnalysisMode.HYBRID);
  }

  @Test
  void shouldMergeInteractionUsingDefaultMetricKeys() {
    Map<String, Object> baseline = Map.of("volume", 100L, "problematic_count", 10L);
    RootCauseSegment h =
        RootCauseSegment.builder()
            .label("p + v")
            .dimensions(new LinkedHashMap<>(Map.of("platform", "x", "app.version", "y")))
            .metrics(Map.of("volume", 20L, "problematic_count", 8L))
            .build();
    RootCauseSegment f =
        RootCauseSegment.builder()
            .label("platform: z")
            .dimensions(new LinkedHashMap<>(Map.of("platform", "z")))
            .metrics(Map.of("volume", 50L, "problematic_count", 5L))
            .build();
    RcaHybridMergeOutcome.Result out =
        RcaHybridMergeOutcome.mergeForInteraction(
            "[test]", baseline, List.of(h), List.of(f), List.of("platform", "app.version"), 10);
    assertThat(out.mode()).isEqualTo(RootCauseAnalysisMode.HYBRID);
    assertThat(out.segments()).isNotEmpty();
    assertThat(out.segments().get(0).getDimensions().size()).isGreaterThanOrEqualTo(2);
  }
}
