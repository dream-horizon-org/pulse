package org.dreamhorizon.pulseserver.service.tenant;

public enum TenantAuditAction {
  CREDENTIALS_CREATED("CREDENTIALS_CREATED"),
  CREDENTIALS_UPDATED("CREDENTIALS_UPDATED"),
  CREDENTIALS_DEACTIVATED("CREDENTIALS_DEACTIVATED"),
  CREDENTIALS_REACTIVATED("CREDENTIALS_REACTIVATED");

  private final String value;

  TenantAuditAction(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
