package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Operators allowed on funnel attribute filters (steps and global).
 */
public enum FunnelFilterOperator {
  EQ,
  NE,
  IN,
  NOT_IN;

  @JsonValue
  public String toJson() {
    return name();
  }

  @JsonCreator
  public static FunnelFilterOperator fromJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String v = value.trim().toUpperCase().replace(' ', '_');
    return Arrays.stream(values())
      .filter(op -> op.name().equals(v))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unknown funnel filter operator: " + value));
  }
}
