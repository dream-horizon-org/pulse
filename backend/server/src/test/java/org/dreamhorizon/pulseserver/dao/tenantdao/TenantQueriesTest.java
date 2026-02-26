package org.dreamhorizon.pulseserver.dao.tenantdao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantQueriesTest {

  @Nested
  class TestQueryConstants {

    @Test
    void shouldHaveInsertTenantQuery() {
      assertNotNull(TenantQueries.INSERT_TENANT);
      assertTrue(TenantQueries.INSERT_TENANT.contains("INSERT INTO tenants"));
      assertTrue(TenantQueries.INSERT_TENANT.contains("tenant_id"));
    }

    @Test
    void shouldHaveGetTenantByIdQuery() {
      assertNotNull(TenantQueries.GET_TENANT_BY_ID);
      assertTrue(TenantQueries.GET_TENANT_BY_ID.contains("SELECT"));
      assertTrue(TenantQueries.GET_TENANT_BY_ID.contains("FROM tenants"));
      assertTrue(TenantQueries.GET_TENANT_BY_ID.contains("tenant_id = ?"));
    }

    @Test
    void shouldHaveGetAllActiveTenantsQuery() {
      assertNotNull(TenantQueries.GET_ALL_ACTIVE_TENANTS);
      assertTrue(TenantQueries.GET_ALL_ACTIVE_TENANTS.contains("SELECT"));
      assertTrue(TenantQueries.GET_ALL_ACTIVE_TENANTS.contains("is_active = TRUE"));
    }

    @Test
    void shouldHaveGetAllTenantsQuery() {
      assertNotNull(TenantQueries.GET_ALL_TENANTS);
      assertTrue(TenantQueries.GET_ALL_TENANTS.contains("SELECT"));
      assertTrue(TenantQueries.GET_ALL_TENANTS.contains("FROM tenants"));
      assertFalse(TenantQueries.GET_ALL_TENANTS.contains("WHERE"));
    }

    @Test
    void shouldHaveUpdateTenantQuery() {
      assertNotNull(TenantQueries.UPDATE_TENANT);
      assertTrue(TenantQueries.UPDATE_TENANT.contains("UPDATE tenants"));
      assertTrue(TenantQueries.UPDATE_TENANT.contains("name = ?"));
    }

    @Test
    void shouldHaveDeactivateTenantQuery() {
      assertNotNull(TenantQueries.DEACTIVATE_TENANT);
      assertTrue(TenantQueries.DEACTIVATE_TENANT.contains("UPDATE"));
      assertTrue(TenantQueries.DEACTIVATE_TENANT.contains("is_active = FALSE"));
    }

    @Test
    void shouldHaveActivateTenantQuery() {
      assertNotNull(TenantQueries.ACTIVATE_TENANT);
      assertTrue(TenantQueries.ACTIVATE_TENANT.contains("UPDATE"));
      assertTrue(TenantQueries.ACTIVATE_TENANT.contains("is_active = TRUE"));
    }

    @Test
    void shouldHaveDeleteTenantQuery() {
      assertNotNull(TenantQueries.DELETE_TENANT);
      assertTrue(TenantQueries.DELETE_TENANT.contains("DELETE FROM tenants"));
    }

    @Test
    void shouldHaveCheckTenantExistsQuery() {
      assertNotNull(TenantQueries.CHECK_TENANT_EXISTS);
      assertTrue(TenantQueries.CHECK_TENANT_EXISTS.contains("SELECT COUNT(*)"));
      assertTrue(TenantQueries.CHECK_TENANT_EXISTS.contains("tenant_id = ?"));
    }
  }

  @Nested
  class TestClassInstantiation {

    @Test
    void shouldBeInstantiable() throws Exception {
      Constructor<TenantQueries> constructor = TenantQueries.class.getDeclaredConstructor();
      assertNotNull(constructor);
      assertFalse(Modifier.isPrivate(constructor.getModifiers()));
      
      TenantQueries instance = new TenantQueries();
      assertNotNull(instance);
    }
  }
}
