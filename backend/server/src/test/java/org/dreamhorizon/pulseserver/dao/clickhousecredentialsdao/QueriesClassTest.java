package org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Queries class to ensure coverage of static class instantiation.
 */
class QueriesClassTest {

  @Test
  void shouldInstantiateQueriesClass() throws Exception {
    // Use reflection to instantiate the class
    Constructor<Queries> constructor = Queries.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    Queries instance = constructor.newInstance();
    assertNotNull(instance);
  }

  @Test
  void shouldVerifyAllQueriesAreNotNull() {
    assertNotNull(Queries.INSERT_CREDENTIALS);
    assertNotNull(Queries.GET_CREDENTIALS_BY_TENANT);
    assertNotNull(Queries.GET_CREDENTIALS_BY_TENANT_INCLUDING_INACTIVE);
    assertNotNull(Queries.GET_ALL_ACTIVE_CREDENTIALS);
    assertNotNull(Queries.UPDATE_CREDENTIALS);
    assertNotNull(Queries.DEACTIVATE_CREDENTIALS);
    assertNotNull(Queries.REACTIVATE_CREDENTIALS);
    assertNotNull(Queries.DELETE_CREDENTIALS);
    assertNotNull(Queries.INSERT_TENANT);
    assertNotNull(Queries.GET_TENANT_BY_ID);
    assertNotNull(Queries.GET_ALL_ACTIVE_TENANTS);
    assertNotNull(Queries.UPDATE_TENANT);
    assertNotNull(Queries.DEACTIVATE_TENANT);
    assertNotNull(Queries.INSERT_AUDIT);
    assertNotNull(Queries.GET_AUDIT_BY_TENANT);
    assertNotNull(Queries.GET_RECENT_AUDITS);
  }

  @Test
  void shouldVerifyQueriesContainExpectedSqlKeywords() {
    assertTrue(Queries.INSERT_CREDENTIALS.toUpperCase().contains("INSERT"));
    assertTrue(Queries.GET_CREDENTIALS_BY_TENANT.toUpperCase().contains("SELECT"));
    assertTrue(Queries.GET_CREDENTIALS_BY_TENANT_INCLUDING_INACTIVE.toUpperCase().contains("SELECT"));
    assertTrue(Queries.GET_ALL_ACTIVE_CREDENTIALS.toUpperCase().contains("SELECT"));
    assertTrue(Queries.UPDATE_CREDENTIALS.toUpperCase().contains("UPDATE"));
    assertTrue(Queries.DEACTIVATE_CREDENTIALS.toUpperCase().contains("UPDATE"));
    assertTrue(Queries.REACTIVATE_CREDENTIALS.toUpperCase().contains("UPDATE"));
    assertTrue(Queries.DELETE_CREDENTIALS.toUpperCase().contains("DELETE"));
    assertTrue(Queries.INSERT_AUDIT.toUpperCase().contains("INSERT"));
    assertTrue(Queries.GET_AUDIT_BY_TENANT.toUpperCase().contains("SELECT"));
    assertTrue(Queries.GET_RECENT_AUDITS.toUpperCase().contains("SELECT"));
  }

  @Test
  void shouldVerifyQueriesReferenceCorrectTables() {
    assertTrue(Queries.INSERT_CREDENTIALS.contains("clickhouse_tenant_credentials"));
    assertTrue(Queries.GET_CREDENTIALS_BY_TENANT.contains("clickhouse_tenant_credentials"));
    assertTrue(Queries.UPDATE_CREDENTIALS.contains("clickhouse_tenant_credentials"));
    assertTrue(Queries.DEACTIVATE_CREDENTIALS.contains("clickhouse_tenant_credentials"));
    assertTrue(Queries.REACTIVATE_CREDENTIALS.contains("clickhouse_tenant_credentials"));
    assertTrue(Queries.INSERT_AUDIT.contains("clickhouse_credential_audit"));
    assertTrue(Queries.GET_AUDIT_BY_TENANT.contains("clickhouse_credential_audit"));
    assertTrue(Queries.GET_RECENT_AUDITS.contains("clickhouse_credential_audit"));
  }

  @Test
  void shouldVerifyQueriesContainPlaceholders() {
    assertTrue(Queries.GET_CREDENTIALS_BY_TENANT.contains("?"));
    assertTrue(Queries.INSERT_CREDENTIALS.contains("?"));
    assertTrue(Queries.INSERT_AUDIT.contains("?"));
  }
}
