package org.dreamhorizon.pulseserver.resources.performance.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum Group {
  USE_CASE_ID("useCaseId");

  private final String value;

  Group(String value) {
    this.value = value;
  }

  @JsonCreator
  public static Group fromValue(String value) {
    for (Group group : Group.values()) {
      if (group.value.equalsIgnoreCase(value)) {
        return group;
      }
    }
    throw new IllegalArgumentException("Invalid Group: " + value);
  }
}
