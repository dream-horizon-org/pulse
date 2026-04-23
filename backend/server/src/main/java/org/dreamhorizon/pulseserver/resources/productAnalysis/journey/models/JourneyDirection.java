package org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum JourneyDirection {
  START,
  END;

  @JsonValue
  public String toJson() {
    return name();
  }

  @JsonCreator
  public static JourneyDirection fromJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Arrays.stream(values())
      .filter(v -> v.name().equalsIgnoreCase(value.trim()))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unknown journey direction: " + value));
  }
}
