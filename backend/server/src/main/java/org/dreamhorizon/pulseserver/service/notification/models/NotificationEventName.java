package org.dreamhorizon.pulseserver.service.notification.models;

public enum NotificationEventName {
  CREATE_INCIDENT("create_incident"),
  ACKNOWLEDGE_INCIDENT("acknowledge_incident"),
  RECOVERED_INCIDENT("recover_incident"),
  CLOSE_INCIDENT("close_incident");

  private final String value;

  NotificationEventName(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
