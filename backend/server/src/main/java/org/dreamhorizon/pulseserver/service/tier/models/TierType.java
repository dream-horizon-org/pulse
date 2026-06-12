package org.dreamhorizon.pulseserver.service.tier.models;

public enum TierType {
  FREE("free"),
  ENTERPRISE("enterprise");

  private final String value;

  TierType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static TierType fromString(String tierType) {
    if (tierType == null) {
      return null;
    }

    for (TierType type : values()) {
      if (type.getValue().equalsIgnoreCase(tierType.trim())) {
        return type;
      }
    }
    throw new IllegalArgumentException("No TierType found for: " + tierType);
  }
}

