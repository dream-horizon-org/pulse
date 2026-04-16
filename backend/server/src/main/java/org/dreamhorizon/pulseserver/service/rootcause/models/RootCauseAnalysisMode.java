package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Layout of root-cause segments: single level vs nested dimensions. */
public enum RootCauseAnalysisMode {
  FLAT("flat"),
  HIERARCHICAL("hierarchical");

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
