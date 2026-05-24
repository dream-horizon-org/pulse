package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Signal source for funnel steps / journey path ({@code anchorEvent} is always a custom event).
 */
public enum AnalysisBasis {
  EVENT,
  SCREEN;

  @JsonValue
  public String toJson() {
    return name();
  }

  @JsonCreator
  public static AnalysisBasis fromJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Arrays.stream(values())
      .filter(v -> v.name().equalsIgnoreCase(value.trim()))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unknown analysisBasis: " + value));
  }

  /** DB / legacy rows without {@code analysis_basis} column value. */
  public static AnalysisBasis fromJsonOrDefault(String value) {
    AnalysisBasis parsed = fromJson(value);
    return parsed != null ? parsed : EVENT;
  }
}
