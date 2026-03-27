package org.dreamhorizon.pulseserver.resources.funnel.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Lifecycle state for a saved funnel definition. */
public enum FunnelDefinitionStatus {
  ACTIVE,
  PAUSED,
  ARCHIVED;

  @JsonValue
  public String toJson() {
    return name();
  }

  @JsonCreator
  public static FunnelDefinitionStatus fromJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Arrays.stream(values())
        .filter(s -> s.name().equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown funnel status: " + value));
  }
}
