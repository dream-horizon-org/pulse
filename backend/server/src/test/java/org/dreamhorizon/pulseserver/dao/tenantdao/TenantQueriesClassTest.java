package org.dreamhorizon.pulseserver.dao.tenantdao;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

/**
 * Tests for the TenantQueries class to ensure coverage of static class instantiation.
 */
class TenantQueriesClassTest {

  @Test
  void shouldInstantiateTenantQueriesClass() throws Exception {
    // Use reflection to instantiate the class
    Constructor<TenantQueries> constructor = TenantQueries.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    TenantQueries instance = constructor.newInstance();
    assertNotNull(instance);
  }

  @Test
  void shouldVerifyAllQueriesAreNotNull() {
    assertNotNull(TenantQueries.INSERT_TENANT);
    assertNotNull(TenantQueries.GET_TENANT_BY_ID);
    assertNotNull(TenantQueries.GET_ALL_ACTIVE_TENANTS);
    assertNotNull(TenantQueries.GET_ALL_TENANTS);
    assertNotNull(TenantQueries.UPDATE_TENANT);
    assertNotNull(TenantQueries.DEACTIVATE_TENANT);
    assertNotNull(TenantQueries.ACTIVATE_TENANT);
    assertNotNull(TenantQueries.DELETE_TENANT);
    assertNotNull(TenantQueries.CHECK_TENANT_EXISTS);
  }

  @Test
  void shouldVerifyQueriesContainExpectedSqlKeywords() {
    assertTrue(TenantQueries.INSERT_TENANT.toUpperCase().contains("INSERT"));
    assertTrue(TenantQueries.GET_TENANT_BY_ID.toUpperCase().contains("SELECT"));
    assertTrue(TenantQueries.GET_ALL_ACTIVE_TENANTS.toUpperCase().contains("SELECT"));
    assertTrue(TenantQueries.GET_ALL_TENANTS.toUpperCase().contains("SELECT"));
    assertTrue(TenantQueries.UPDATE_TENANT.toUpperCase().contains("UPDATE"));
    assertTrue(TenantQueries.DEACTIVATE_TENANT.toUpperCase().contains("UPDATE"));
    assertTrue(TenantQueries.ACTIVATE_TENANT.toUpperCase().contains("UPDATE"));
    assertTrue(TenantQueries.DELETE_TENANT.toUpperCase().contains("DELETE"));
    assertTrue(TenantQueries.CHECK_TENANT_EXISTS.toUpperCase().contains("SELECT"));
  }

  @Test
  void shouldVerifyQueriesReferenceTenantsTable() {
    assertTrue(TenantQueries.INSERT_TENANT.contains("tenants"));
    assertTrue(TenantQueries.GET_TENANT_BY_ID.contains("tenants"));
    assertTrue(TenantQueries.GET_ALL_ACTIVE_TENANTS.contains("tenants"));
    assertTrue(TenantQueries.GET_ALL_TENANTS.contains("tenants"));
    assertTrue(TenantQueries.UPDATE_TENANT.contains("tenants"));
    assertTrue(TenantQueries.DEACTIVATE_TENANT.contains("tenants"));
    assertTrue(TenantQueries.ACTIVATE_TENANT.contains("tenants"));
    assertTrue(TenantQueries.DELETE_TENANT.contains("tenants"));
    assertTrue(TenantQueries.CHECK_TENANT_EXISTS.contains("tenants"));
  }

  @Test
  void shouldVerifyQueriesContainPlaceholders() {
    assertTrue(TenantQueries.GET_TENANT_BY_ID.contains("?"));
    assertTrue(TenantQueries.INSERT_TENANT.contains("?"));
    assertTrue(TenantQueries.UPDATE_TENANT.contains("?"));
    assertTrue(TenantQueries.DEACTIVATE_TENANT.contains("?"));
    assertTrue(TenantQueries.ACTIVATE_TENANT.contains("?"));
    assertTrue(TenantQueries.DELETE_TENANT.contains("?"));
    assertTrue(TenantQueries.CHECK_TENANT_EXISTS.contains("?"));
  }

  @Test
  void shouldVerifyActiveTenantQueriesFilterByIsActive() {
    assertTrue(TenantQueries.GET_ALL_ACTIVE_TENANTS.contains("is_active"));
  }
}
