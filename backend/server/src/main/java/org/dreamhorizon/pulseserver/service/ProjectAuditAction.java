package org.dreamhorizon.pulseserver.service;

public enum ProjectAuditAction {
  CREDENTIALS_SETUP("CREDENTIALS_SETUP"),
  CREDENTIALS_UPDATED("CREDENTIALS_UPDATED"),
  CREDENTIALS_REMOVED("CREDENTIALS_REMOVED"),
  CREDENTIALS_ROTATED("CREDENTIALS_ROTATED");

  private final String value;

  ProjectAuditAction(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
