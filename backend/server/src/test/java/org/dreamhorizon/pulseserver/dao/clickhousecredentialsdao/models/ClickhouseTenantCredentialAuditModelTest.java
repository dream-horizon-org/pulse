package org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseTenantCredentialAudit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClickhouseTenantCredentialAuditModelTest {

  @Nested
  class TestBuilder {

    @Test
    void shouldBuildWithAllFields() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .id(1L)
          .projectId("test_project")
          .action("CREDENTIALS_CREATED")
          .performedBy("admin@example.com")
          .details("{\"key\":\"value\"}")
          .createdAt("2026-01-01T00:00:00")
          .build();

      assertEquals(1L, audit.getId());
      assertEquals("test_project", audit.getProjectId());
      assertEquals("CREDENTIALS_CREATED", audit.getAction());
      assertEquals("admin@example.com", audit.getPerformedBy());
      assertEquals("{\"key\":\"value\"}", audit.getDetails());
      assertEquals("2026-01-01T00:00:00", audit.getCreatedAt());
    }

    @Test
    void shouldBuildWithMinimalFields() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .projectId("test_project")
          .action("CREDENTIALS_CREATED")
          .build();

      assertEquals("test_project", audit.getProjectId());
      assertEquals("CREDENTIALS_CREATED", audit.getAction());
      assertNull(audit.getId());
    }
  }

  @Nested
  class TestSettersAndGetters {

    @Test
    void shouldSetAndGetAllFields() {
      ClickhouseTenantCredentialAudit audit = new ClickhouseTenantCredentialAudit();

      audit.setId(2L);
      audit.setProjectId("project_abc");
      audit.setAction("CREDENTIALS_UPDATED");
      audit.setPerformedBy("user@example.com");
      audit.setDetails("{\"reason\":\"rotation\"}");
      audit.setCreatedAt("2026-02-01T00:00:00");

      assertEquals(2L, audit.getId());
      assertEquals("project_abc", audit.getProjectId());
      assertEquals("CREDENTIALS_UPDATED", audit.getAction());
      assertEquals("user@example.com", audit.getPerformedBy());
      assertEquals("{\"reason\":\"rotation\"}", audit.getDetails());
      assertEquals("2026-02-01T00:00:00", audit.getCreatedAt());
    }
  }

  @Nested
  class TestEqualsAndHashCode {

    @Test
    void shouldBeEqualForSameValues() {
      ClickhouseTenantCredentialAudit audit1 = ClickhouseTenantCredentialAudit.builder()
          .id(1L)
          .projectId("test_project")
          .action("CREDENTIALS_CREATED")
          .build();

      ClickhouseTenantCredentialAudit audit2 = ClickhouseTenantCredentialAudit.builder()
          .id(1L)
          .projectId("test_project")
          .action("CREDENTIALS_CREATED")
          .build();

      assertEquals(audit1, audit2);
      assertEquals(audit1.hashCode(), audit2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentValues() {
      ClickhouseTenantCredentialAudit audit1 = ClickhouseTenantCredentialAudit.builder()
          .id(1L)
          .action("CREDENTIALS_CREATED")
          .build();

      ClickhouseTenantCredentialAudit audit2 = ClickhouseTenantCredentialAudit.builder()
          .id(2L)
          .action("CREDENTIALS_UPDATED")
          .build();

      assertNotEquals(audit1, audit2);
    }

    @Test
    void shouldBeEqualToItself() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .projectId("test")
          .projectId("test")
          .build();

      assertEquals(audit, audit);
    }

    @Test
    void shouldNotBeEqualToNull() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .projectId("test")
          .projectId("test")
          .build();

      assertNotEquals(null, audit);
    }

    @Test
    void shouldNotBeEqualToDifferentType() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .projectId("test")
          .projectId("test")
          .build();

      assertNotEquals("string", audit);
    }
  }

  @Nested
  class TestToString {

    @Test
    void shouldGenerateToString() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .id(1L)
          .projectId("test_project")
          .action("CREDENTIALS_CREATED")
          .performedBy("admin")
          .build();

      String toString = audit.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("test_project"));
      assertTrue(toString.contains("CREDENTIALS_CREATED"));
    }
  }

  @Nested
  class TestAllArgsConstructor {

    @Test
    void shouldCreateWithAllArgsConstructor() {
      ClickhouseTenantCredentialAudit audit = new ClickhouseTenantCredentialAudit(
          1L, "project", "CREATE", "admin", "details", "2026-01-01"
      );

      assertEquals(1L, audit.getId());
      assertEquals("project", audit.getProjectId());
      assertEquals("CREATE", audit.getAction());
      assertEquals("admin", audit.getPerformedBy());
      assertEquals("details", audit.getDetails());
      assertEquals("2026-01-01", audit.getCreatedAt());
    }
  }

  @Nested
  class TestNoArgsConstructor {

    @Test
    void shouldCreateWithNoArgsConstructor() {
      ClickhouseTenantCredentialAudit audit = new ClickhouseTenantCredentialAudit();

      assertNull(audit.getId());
      assertNull(audit.getProjectId());
      assertNull(audit.getProjectId());
      assertNull(audit.getAction());
    }
  }
}
