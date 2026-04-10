package org.dreamhorizon.pulseserver.service.usagelimit.models;

public enum WindowType {
  WEEKLY("weekly"),
  MONTHLY("monthly"),
  YEARLY("yearly"),
  TOTAL("total");

  private final String value;

  WindowType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static WindowType fromString(String windowType) {
    if (windowType == null) {
      return null;
    }

    for (WindowType type : values()) {
      if (type.getValue().equalsIgnoreCase(windowType.trim())) {
        return type;
      }
    }
    throw new IllegalArgumentException("No WindowType found for: " + windowType);
  }
}

