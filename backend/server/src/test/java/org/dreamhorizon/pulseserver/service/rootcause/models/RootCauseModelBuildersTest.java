package org.dreamhorizon.pulseserver.service.rootcause.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RootCauseModelBuildersTest {

  @Test
  void shouldRoundTripRootCauseResultBuilder() {
    RootCauseResult built =
        RootCauseResult.builder()
            .baseline(Map.of("volume", 1))
            .segments(List.of())
            .mode(RootCauseAnalysisMode.FLAT)
            .cachedAt(Instant.parse("2025-01-01T00:00:00Z"))
            .everythingGood(true)
            .noDataAvailable(false)
            .message("ok")
            .build();

    RootCauseResult copy =
        built.toBuilder().mode(RootCauseAnalysisMode.HIERARCHICAL).build();

    assertThat(copy.getMode()).isEqualTo(RootCauseAnalysisMode.HIERARCHICAL);
    assertThat(copy.getBaseline()).containsEntry("volume", 1);
  }

  @Test
  void shouldBuildRootCauseSegment() {
    RootCauseSegment seg =
        RootCauseSegment.builder()
            .label("Platform: Android")
            .dimensions(Map.of("Platform", "Android"))
            .metrics(Map.of("volume", 10L))
            .deltas(Map.of("volume", 5.0))
            .build();

    assertThat(seg.getLabel()).contains("Android");
    assertThat(seg.getDeltas()).containsEntry("volume", 5.0);
  }

  @Test
  void shouldDefaultUnknownStoredModeToFlat() {
    assertThat(RootCauseAnalysisMode.fromWireValue("hierarchical"))
        .isEqualTo(RootCauseAnalysisMode.HIERARCHICAL);
    assertThat(RootCauseAnalysisMode.fromWireValue("unknown"))
        .isEqualTo(RootCauseAnalysisMode.FLAT);
    assertThat(RootCauseAnalysisMode.fromWireValue(null)).isEqualTo(RootCauseAnalysisMode.FLAT);
  }
}
