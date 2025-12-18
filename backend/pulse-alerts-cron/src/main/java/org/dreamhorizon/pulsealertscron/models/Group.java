package org.dreamhorizon.pulsealertscron.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum Group {
  USE_CASE_ID("useCaseId");

  Group(String value) {
    this.value = value;
  }

  private final String value;

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
