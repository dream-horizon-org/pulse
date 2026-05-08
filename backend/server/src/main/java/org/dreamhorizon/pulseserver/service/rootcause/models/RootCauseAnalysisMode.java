package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
}
