package org.dreamhorizon.pulseserver.dao.tenantdao.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantModelTest {

  @Nested
  class TestBuilder {

    @Test
    void shouldBuildWithAllFields() {
      Tenant tenant = Tenant.builder()
          .tenantId("test_tenant")
          .name("Test Tenant")
          .description("Test Description")
          .isActive(true)
          .createdAt("2026-01-01T00:00:00")
          .updatedAt("2026-01-01T00:00:00")
          .gcpTenantId("gcp-123")
          .domainName("test.example.com")
          .build();

      assertEquals("test_tenant", tenant.getTenantId());
      assertEquals("Test Tenant", tenant.getName());
      assertEquals("Test Description", tenant.getDescription());
      assertTrue(tenant.getIsActive());
      assertEquals("2026-01-01T00:00:00", tenant.getCreatedAt());
      assertEquals("2026-01-01T00:00:00", tenant.getUpdatedAt());
      assertEquals("gcp-123", tenant.getGcpTenantId());
      assertEquals("test.example.com", tenant.getDomainName());
    }

    @Test
    void shouldBuildWithMinimalFields() {
      Tenant tenant = Tenant.builder()
          .tenantId("test_tenant")
          .build();

      assertEquals("test_tenant", tenant.getTenantId());
      assertNull(tenant.getName());
      assertNull(tenant.getDescription());
    }
  }

  @Nested
  class TestSettersAndGetters {

    @Test
    void shouldSetAndGetAllFields() {
      Tenant tenant = new Tenant();
      
      tenant.setTenantId("tenant_abc");
      tenant.setName("Tenant ABC");
      tenant.setDescription("Description ABC");
      tenant.setIsActive(false);
      tenant.setCreatedAt("2026-02-01T00:00:00");
      tenant.setUpdatedAt("2026-02-02T00:00:00");
      tenant.setGcpTenantId("gcp-456");
      tenant.setDomainName("abc.example.com");

      assertEquals("tenant_abc", tenant.getTenantId());
      assertEquals("Tenant ABC", tenant.getName());
      assertEquals("Description ABC", tenant.getDescription());
      assertFalse(tenant.getIsActive());
      assertEquals("2026-02-01T00:00:00", tenant.getCreatedAt());
      assertEquals("2026-02-02T00:00:00", tenant.getUpdatedAt());
      assertEquals("gcp-456", tenant.getGcpTenantId());
      assertEquals("abc.example.com", tenant.getDomainName());
    }
  }

  @Nested
  class TestEqualsAndHashCode {

    @Test
    void shouldBeEqualForSameValues() {
      Tenant tenant1 = Tenant.builder()
          .tenantId("test_tenant")
          .name("Test")
          .isActive(true)
          .build();

      Tenant tenant2 = Tenant.builder()
          .tenantId("test_tenant")
          .name("Test")
          .isActive(true)
          .build();

      assertEquals(tenant1, tenant2);
      assertEquals(tenant1.hashCode(), tenant2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentValues() {
      Tenant tenant1 = Tenant.builder()
          .tenantId("tenant_a")
          .name("Tenant A")
          .build();

      Tenant tenant2 = Tenant.builder()
          .tenantId("tenant_b")
          .name("Tenant B")
          .build();

      assertNotEquals(tenant1, tenant2);
    }

    @Test
    void shouldBeEqualToItself() {
      Tenant tenant = Tenant.builder()
          .tenantId("test")
          .build();

      assertEquals(tenant, tenant);
    }

    @Test
    void shouldNotBeEqualToNull() {
      Tenant tenant = Tenant.builder()
          .tenantId("test")
          .build();

      assertNotEquals(null, tenant);
    }

    @Test
    void shouldNotBeEqualToDifferentType() {
      Tenant tenant = Tenant.builder()
          .tenantId("test")
          .build();

      assertNotEquals("string", tenant);
    }
  }

  @Nested
  class TestToString {

    @Test
    void shouldGenerateToString() {
      Tenant tenant = Tenant.builder()
          .tenantId("test_tenant")
          .name("Test Tenant")
          .isActive(true)
          .build();

      String toString = tenant.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("test_tenant"));
      assertTrue(toString.contains("Test Tenant"));
    }
  }

  @Nested
  class TestAllArgsConstructor {

    @Test
    void shouldCreateWithAllArgsConstructor() {
      Tenant tenant = new Tenant(
          "tenant", "name", "desc", true, "created", "updated", "gcp", "domain"
      );

      assertEquals("tenant", tenant.getTenantId());
      assertEquals("name", tenant.getName());
      assertEquals("desc", tenant.getDescription());
      assertTrue(tenant.getIsActive());
      assertEquals("created", tenant.getCreatedAt());
      assertEquals("updated", tenant.getUpdatedAt());
      assertEquals("gcp", tenant.getGcpTenantId());
      assertEquals("domain", tenant.getDomainName());
    }
  }

  @Nested
  class TestNoArgsConstructor {

    @Test
    void shouldCreateWithNoArgsConstructor() {
      Tenant tenant = new Tenant();

      assertNull(tenant.getTenantId());
      assertNull(tenant.getName());
      assertNull(tenant.getDescription());
    }
  }
}
