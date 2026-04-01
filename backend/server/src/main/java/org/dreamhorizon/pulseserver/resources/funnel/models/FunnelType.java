package org.dreamhorizon.pulseserver.resources.funnel.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Persisted and API funnel classification: automatic vs one-shot. */
public enum FunnelType {
  AUTO,
  ONCE;

  @JsonValue
  public String toJson() {
    return name();
  }

  @JsonCreator
  public static FunnelType fromJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Arrays.stream(values())
        .filter(v -> v.name().equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown funnelType: " + value));
  }
}
