package org.dreamhorizon.pulseserver.service.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class TenantAuditActionTest {

  @Test
  void shouldReturnCorrectValueForCredentialsCreated() {
    assertEquals("CREDENTIALS_CREATED", TenantAuditAction.CREDENTIALS_CREATED.getValue());
  }

  @Test
  void shouldReturnCorrectValueForCredentialsUpdated() {
    assertEquals("CREDENTIALS_UPDATED", TenantAuditAction.CREDENTIALS_UPDATED.getValue());
  }

  @Test
  void shouldReturnCorrectValueForCredentialsDeactivated() {
    assertEquals("CREDENTIALS_DEACTIVATED", TenantAuditAction.CREDENTIALS_DEACTIVATED.getValue());
  }

  @Test
  void shouldReturnCorrectValueForCredentialsReactivated() {
    assertEquals("CREDENTIALS_REACTIVATED", TenantAuditAction.CREDENTIALS_REACTIVATED.getValue());
  }

  @Test
  void shouldHaveFourEnumValues() {
    TenantAuditAction[] values = TenantAuditAction.values();
    assertEquals(4, values.length);
  }

  @Test
  void shouldReturnEnumFromName() {
    assertEquals(TenantAuditAction.CREDENTIALS_CREATED, 
        TenantAuditAction.valueOf("CREDENTIALS_CREATED"));
    assertEquals(TenantAuditAction.CREDENTIALS_UPDATED, 
        TenantAuditAction.valueOf("CREDENTIALS_UPDATED"));
    assertEquals(TenantAuditAction.CREDENTIALS_DEACTIVATED, 
        TenantAuditAction.valueOf("CREDENTIALS_DEACTIVATED"));
    assertEquals(TenantAuditAction.CREDENTIALS_REACTIVATED, 
        TenantAuditAction.valueOf("CREDENTIALS_REACTIVATED"));
  }

  @Test
  void shouldHaveNonNullValues() {
    for (TenantAuditAction action : TenantAuditAction.values()) {
      assertNotNull(action.getValue());
    }
  }
}
