package org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.Queries;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QueriesTest {

  @Nested
  class TestQueryConstants {

    @Test
    void shouldHaveInsertCredentialsQuery() {
      assertNotNull(Queries.INSERT_CREDENTIALS);
      assertTrue(Queries.INSERT_CREDENTIALS.contains("INSERT INTO clickhouse_tenant_credentials"));
      assertTrue(Queries.INSERT_CREDENTIALS.contains("tenant_id"));
    }

    @Test
    void shouldHaveGetCredentialsByTenantQuery() {
      assertNotNull(Queries.GET_CREDENTIALS_BY_TENANT);
      assertTrue(Queries.GET_CREDENTIALS_BY_TENANT.contains("SELECT"));
      assertTrue(Queries.GET_CREDENTIALS_BY_TENANT.contains("clickhouse_tenant_credentials"));
      assertTrue(Queries.GET_CREDENTIALS_BY_TENANT.contains("is_active = TRUE"));
    }

    @Test
    void shouldHaveGetCredentialsByTenantIncludingInactiveQuery() {
      assertNotNull(Queries.GET_CREDENTIALS_BY_TENANT_INCLUDING_INACTIVE);
      assertTrue(Queries.GET_CREDENTIALS_BY_TENANT_INCLUDING_INACTIVE.contains("SELECT"));
      assertFalse(Queries.GET_CREDENTIALS_BY_TENANT_INCLUDING_INACTIVE.contains("is_active = TRUE"));
    }

    @Test
    void shouldHaveGetAllActiveCredentialsQuery() {
      assertNotNull(Queries.GET_ALL_ACTIVE_CREDENTIALS);
      assertTrue(Queries.GET_ALL_ACTIVE_CREDENTIALS.contains("SELECT"));
      assertTrue(Queries.GET_ALL_ACTIVE_CREDENTIALS.contains("is_active = TRUE"));
    }

    @Test
    void shouldHaveUpdateCredentialsQuery() {
      assertNotNull(Queries.UPDATE_CREDENTIALS);
      assertTrue(Queries.UPDATE_CREDENTIALS.contains("UPDATE"));
      assertTrue(Queries.UPDATE_CREDENTIALS.contains("clickhouse_password_encrypted"));
    }

    @Test
    void shouldHaveDeactivateCredentialsQuery() {
      assertNotNull(Queries.DEACTIVATE_CREDENTIALS);
      assertTrue(Queries.DEACTIVATE_CREDENTIALS.contains("UPDATE"));
      assertTrue(Queries.DEACTIVATE_CREDENTIALS.contains("is_active = FALSE"));
    }

    @Test
    void shouldHaveReactivateCredentialsQuery() {
      assertNotNull(Queries.REACTIVATE_CREDENTIALS);
      assertTrue(Queries.REACTIVATE_CREDENTIALS.contains("UPDATE"));
      assertTrue(Queries.REACTIVATE_CREDENTIALS.contains("is_active = TRUE"));
    }

    @Test
    void shouldHaveDeleteCredentialsQuery() {
      assertNotNull(Queries.DELETE_CREDENTIALS);
      assertTrue(Queries.DELETE_CREDENTIALS.contains("DELETE FROM"));
    }

    @Test
    void shouldHaveInsertTenantQuery() {
      assertNotNull(Queries.INSERT_TENANT);
      assertTrue(Queries.INSERT_TENANT.contains("INSERT INTO tenants"));
    }

    @Test
    void shouldHaveGetTenantByIdQuery() {
      assertNotNull(Queries.GET_TENANT_BY_ID);
      assertTrue(Queries.GET_TENANT_BY_ID.contains("SELECT"));
      assertTrue(Queries.GET_TENANT_BY_ID.contains("FROM tenants"));
    }

    @Test
    void shouldHaveGetAllActiveTenantsQuery() {
      assertNotNull(Queries.GET_ALL_ACTIVE_TENANTS);
      assertTrue(Queries.GET_ALL_ACTIVE_TENANTS.contains("SELECT"));
      assertTrue(Queries.GET_ALL_ACTIVE_TENANTS.contains("is_active = TRUE"));
    }

    @Test
    void shouldHaveUpdateTenantQuery() {
      assertNotNull(Queries.UPDATE_TENANT);
      assertTrue(Queries.UPDATE_TENANT.contains("UPDATE tenants"));
    }

    @Test
    void shouldHaveDeactivateTenantQuery() {
      assertNotNull(Queries.DEACTIVATE_TENANT);
      assertTrue(Queries.DEACTIVATE_TENANT.contains("is_active = FALSE"));
    }

    @Test
    void shouldHaveInsertAuditQuery() {
      assertNotNull(Queries.INSERT_AUDIT);
      assertTrue(Queries.INSERT_AUDIT.contains("INSERT INTO clickhouse_credential_audit"));
    }

    @Test
    void shouldHaveGetAuditByTenantQuery() {
      assertNotNull(Queries.GET_AUDIT_BY_PROJECT);
      assertTrue(Queries.GET_AUDIT_BY_PROJECT.contains("clickhouse_credential_audit"));
      assertTrue(Queries.GET_AUDIT_BY_PROJECT.contains("ORDER BY created_at DESC"));
    }

    @Test
    void shouldHaveGetRecentAuditsQuery() {
      assertNotNull(Queries.GET_RECENT_AUDITS);
      assertTrue(Queries.GET_RECENT_AUDITS.contains("LIMIT"));
    }
  }

  @Nested
  class TestClassInstantiation {

    @Test
    void shouldBeInstantiable() throws Exception {
      // This test ensures the class can be instantiated (covers the implicit constructor)
      Constructor<Queries> constructor = Queries.class.getDeclaredConstructor();
      assertNotNull(constructor);

      // The class should have a default constructor
      assertFalse(Modifier.isPrivate(constructor.getModifiers()));

      // Create an instance to cover the constructor
      Queries instance = new Queries();
      assertNotNull(instance);
    }
  }
}
