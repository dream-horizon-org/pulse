package org.dreamhorizon.pulseserver.service.notification.models;

public enum NotificationEventName {
  NEW_INCIDENT("new_incident");
  // future events: INCIDENT_RESOLVED, ALERT_TRIGGERED, etc.

  private final String value;

  NotificationEventName(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
