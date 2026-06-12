package org.dreamhorizon.pulseserver.service.interaction.models;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum InteractionStatus {
  RUNNING, STOPPED, DELETED;

  public static InteractionStatus fromString(String interactionStatus) {
    if (interactionStatus == null) {
      return null;
    }

    for (InteractionStatus s : values()) {
      if (s.name().equalsIgnoreCase(interactionStatus.trim())) {
        return s;
      }
    }
    throw new IllegalArgumentException("No enum constant InteractionStatus." + interactionStatus);
  }

  @JsonCreator
  public static InteractionStatus fromJson(String name) {
    return fromString(name);
  }
}
