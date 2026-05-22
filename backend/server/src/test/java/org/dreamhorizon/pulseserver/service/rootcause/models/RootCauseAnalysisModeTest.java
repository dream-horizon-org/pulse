package org.dreamhorizon.pulseserver.service.rootcause.models;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class RootCauseAnalysisModeTest {

  @Test
  void forSegmentShapeAfterGate_emptyMeansFlat() {
    Assertions.assertThat(RootCauseAnalysisMode.forSegmentShapeAfterGate(null))
        .isEqualTo(RootCauseAnalysisMode.FLAT);
    Assertions.assertThat(RootCauseAnalysisMode.forSegmentShapeAfterGate(List.of()))
        .isEqualTo(RootCauseAnalysisMode.FLAT);
  }

  @Test
  void forSegmentShapeAfterGate_classificationByDimensionCardinality() {
    RootCauseSegment one =
        RootCauseSegment.builder().dimensions(dim("Platform", "Android")).build();
    RootCauseSegment two =
        RootCauseSegment.builder()
            .dimensions(Map.of("Platform", "Android", "OsVersion", "14"))
            .build();

    Assertions.assertThat(RootCauseAnalysisMode.forSegmentShapeAfterGate(List.of(one)))
        .isEqualTo(RootCauseAnalysisMode.FLAT);
    Assertions.assertThat(RootCauseAnalysisMode.forSegmentShapeAfterGate(List.of(two)))
        .isEqualTo(RootCauseAnalysisMode.HIERARCHICAL);
    Assertions.assertThat(RootCauseAnalysisMode.forSegmentShapeAfterGate(List.of(two, one)))
        .isEqualTo(RootCauseAnalysisMode.HYBRID);
  }

  private static Map<String, String> dim(String k, String v) {
    return new LinkedHashMap<>(Map.of(k, v));
  }
}
