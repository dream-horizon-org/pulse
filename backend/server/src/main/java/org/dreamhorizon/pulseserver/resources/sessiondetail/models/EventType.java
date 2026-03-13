package org.dreamhorizon.pulseserver.resources.sessiondetail.models;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EventType {
  INTERACTION("interaction"),
  NAVIGATION("navigation"),
  APP_START("app_start"),
  API_CALL("api_call"),
  ANR("anr"),
  CRASH("crash");

  private final String value;

  EventType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public static EventType fromString(String text) {
    for (EventType t : values()) {
      if (t.value.equalsIgnoreCase(text)) {
        return t;
      }
    }
    return null;
  }
}
