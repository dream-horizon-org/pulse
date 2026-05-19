package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/**
 * Layout of root-cause segments: single level vs nested dimensions.
 *
 * <p><b>Not</b> {@link org.dreamhorizon.pulseserver.config.RootCauseConfig#isHybridDimensionOrderingEnabled()}:
 * that flag only reorders dimension columns before segmentation; {@link #HYBRID} describes the
 * merged flat+hierarchy <em>output</em> when 2D+ and 1D tiers both appear.
 */
public enum RootCauseAnalysisMode {
  FLAT("flat"),
  HIERARCHICAL("hierarchical"),
  /**
   * Hierarchical 2D+ segments first, then flat 1D segments (merged pipeline). Wire value {@code hybrid}.
   */
  HYBRID("hybrid");

  private final String wireValue;

  RootCauseAnalysisMode(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String getWireValue() {
    return wireValue;
  }

  /**
   * Parses stored/API values; unknown or blank values default to {@link #FLAT} for resilience.
   */
  @JsonCreator
  public static RootCauseAnalysisMode fromWireValue(String value) {
    if (value == null || value.isBlank()) {
      return FLAT;
    }
    for (RootCauseAnalysisMode mode : values()) {
      if (mode.wireValue.equals(value)) {
        return mode;
      }
    }
    return FLAT;
  }

  /**
   * Recompute mode from segment dimension counts after post-merge filters (e.g. baseline signal gate).
   * Treats {@code dimensions == null} as empty.
   */
  public static RootCauseAnalysisMode forSegmentShapeAfterGate(List<RootCauseSegment> segments) {
    if (segments == null || segments.isEmpty()) {
      return FLAT;
    }
    boolean anyMulti = false;
    boolean anySingle = false;
    for (RootCauseSegment s : segments) {
      if (s == null) {
        continue;
      }
      int size = s.getDimensions() == null ? 0 : s.getDimensions().size();
      if (size >= 2) {
        anyMulti = true;
      } else if (size == 1) {
        anySingle = true;
      }
    }
    if (!anyMulti) {
      return FLAT;
    }
    if (!anySingle) {
      return HIERARCHICAL;
    }
    return HYBRID;
  }
}
