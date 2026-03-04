package org.dreamhorizon.pulseserver.service.usagelimit.models;

public enum DataType {
  INTEGER("INTEGER"),
  BOOLEAN("BOOLEAN");

  private final String value;

  DataType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static DataType fromString(String dataType) {
    if (dataType == null) {
      return null;
    }

    for (DataType type : values()) {
      if (type.getValue().equalsIgnoreCase(dataType.trim())) {
        return type;
      }
    }
    throw new IllegalArgumentException("No DataType found for: " + dataType);
  }
}

