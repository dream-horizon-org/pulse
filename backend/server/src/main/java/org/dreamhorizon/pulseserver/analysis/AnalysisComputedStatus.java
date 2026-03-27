package org.dreamhorizon.pulseserver.analysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Computed UI status from analysis job + AUTO|ONCE (funnel or journey). */
public enum AnalysisComputedStatus {
  ACTIVE,
  IN_PROGRESS,
  WARN,
  PENDING,
  FAILED,
  COMPLETED;

  @JsonValue
  public String toJson() {
    return name();
  }

  @JsonCreator
  public static AnalysisComputedStatus fromJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Arrays.stream(values())
        .filter(s -> s.name().equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown analysis computed status: " + value));
  }
}
