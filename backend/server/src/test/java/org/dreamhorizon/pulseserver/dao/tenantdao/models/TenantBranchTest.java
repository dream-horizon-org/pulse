package org.dreamhorizon.pulseserver.dao.tenantdao.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for Tenant model to cover all Lombok branches.
 */
class TenantBranchTest {

  @Nested
  class TestEqualsAllBranches {

    @Test
    void equalsShouldReturnTrueForSameObject() {
      Tenant tenant = createFullTenant();
      assertTrue(tenant.equals(tenant));
    }

    @Test
    void equalsShouldReturnFalseForNull() {
      Tenant tenant = createFullTenant();
      assertFalse(tenant.equals(null));
    }

    @Test
    void equalsShouldReturnFalseForDifferentClass() {
      Tenant tenant = createFullTenant();
      assertFalse(tenant.equals("not a tenant"));
      assertFalse(tenant.equals(Integer.valueOf(1)));
    }

    @Test
    void equalsShouldReturnTrueForEqualObjects() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      assertTrue(tenant1.equals(tenant2));
      assertTrue(tenant2.equals(tenant1));
    }

    @Test
    void equalsShouldReturnFalseWhenTenantIdDiffers() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      tenant2.setTenantId("different");
      assertFalse(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldReturnFalseWhenNameDiffers() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      tenant2.setName("different");
      assertFalse(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldReturnFalseWhenDescriptionDiffers() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      tenant2.setDescription("different");
      assertFalse(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldReturnFalseWhenIsActiveDiffers() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      tenant2.setIsActive(false);
      assertFalse(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldReturnFalseWhenCreatedAtDiffers() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      tenant2.setCreatedAt("different");
      assertFalse(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldReturnFalseWhenUpdatedAtDiffers() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      tenant2.setUpdatedAt("different");
      assertFalse(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldReturnFalseWhenGcpTenantIdDiffers() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      tenant2.setGcpTenantId("different");
      assertFalse(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldReturnFalseWhenDomainNameDiffers() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      tenant2.setDomainName("different");
      assertFalse(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldHandleNullTenantId() {
      Tenant tenant1 = createFullTenant();
      tenant1.setTenantId(null);
      Tenant tenant2 = createFullTenant();
      tenant2.setTenantId(null);
      assertTrue(tenant1.equals(tenant2));
      
      Tenant tenant3 = createFullTenant();
      assertFalse(tenant1.equals(tenant3));
      assertFalse(tenant3.equals(tenant1));
    }

    @Test
    void equalsShouldHandleNullName() {
      Tenant tenant1 = createFullTenant();
      tenant1.setName(null);
      Tenant tenant2 = createFullTenant();
      tenant2.setName(null);
      assertTrue(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldHandleNullDescription() {
      Tenant tenant1 = createFullTenant();
      tenant1.setDescription(null);
      Tenant tenant2 = createFullTenant();
      tenant2.setDescription(null);
      assertTrue(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldHandleNullIsActive() {
      Tenant tenant1 = createFullTenant();
      tenant1.setIsActive(null);
      Tenant tenant2 = createFullTenant();
      tenant2.setIsActive(null);
      assertTrue(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldHandleNullGcpTenantId() {
      Tenant tenant1 = createFullTenant();
      tenant1.setGcpTenantId(null);
      Tenant tenant2 = createFullTenant();
      tenant2.setGcpTenantId(null);
      assertTrue(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldHandleNullDomainName() {
      Tenant tenant1 = createFullTenant();
      tenant1.setDomainName(null);
      Tenant tenant2 = createFullTenant();
      tenant2.setDomainName(null);
      assertTrue(tenant1.equals(tenant2));
    }

    @Test
    void equalsShouldHandleAllNullFields() {
      Tenant tenant1 = new Tenant();
      Tenant tenant2 = new Tenant();
      assertTrue(tenant1.equals(tenant2));
    }
  }

  @Nested
  class TestHashCodeAllBranches {

    @Test
    void hashCodeShouldBeConsistent() {
      Tenant tenant = createFullTenant();
      int hash1 = tenant.hashCode();
      int hash2 = tenant.hashCode();
      assertEquals(hash1, hash2);
    }

    @Test
    void hashCodeShouldBeEqualForEqualObjects() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      assertEquals(tenant1.hashCode(), tenant2.hashCode());
    }

    @Test
    void hashCodeShouldDifferForDifferentObjects() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      tenant2.setTenantId("different");
      assertNotEquals(tenant1.hashCode(), tenant2.hashCode());
    }

    @Test
    void hashCodeShouldHandleNullFields() {
      Tenant tenant = new Tenant();
      int hash = tenant.hashCode();
      assertNotNull(hash);
    }
  }

  @Nested
  class TestToStringAllBranches {

    @Test
    void toStringShouldNotReturnNull() {
      Tenant tenant = createFullTenant();
      assertNotNull(tenant.toString());
    }

    @Test
    void toStringShouldContainFieldNames() {
      Tenant tenant = createFullTenant();
      String str = tenant.toString();
      assertTrue(str.contains("tenantId"));
      assertTrue(str.contains("name"));
      assertTrue(str.contains("isActive"));
    }

    @Test
    void toStringShouldHandleNullFields() {
      Tenant tenant = new Tenant();
      String str = tenant.toString();
      assertNotNull(str);
    }
  }

  @Nested
  class TestBuilderAllBranches {

    @Test
    void builderShouldCreateObjectWithAllFields() {
      Tenant tenant = Tenant.builder()
          .tenantId("tenant1")
          .name("Test Tenant")
          .description("Description")
          .isActive(true)
          .createdAt("2024-01-01")
          .updatedAt("2024-01-02")
          .gcpTenantId("gcp1")
          .domainName("example.com")
          .build();
      
      assertEquals("tenant1", tenant.getTenantId());
      assertEquals("Test Tenant", tenant.getName());
      assertEquals("Description", tenant.getDescription());
      assertTrue(tenant.getIsActive());
      assertEquals("2024-01-01", tenant.getCreatedAt());
      assertEquals("2024-01-02", tenant.getUpdatedAt());
      assertEquals("gcp1", tenant.getGcpTenantId());
      assertEquals("example.com", tenant.getDomainName());
    }

    @Test
    void builderShouldHandleNullValues() {
      Tenant tenant = Tenant.builder()
          .tenantId(null)
          .name(null)
          .description(null)
          .isActive(null)
          .createdAt(null)
          .updatedAt(null)
          .gcpTenantId(null)
          .domainName(null)
          .build();
      
      assertNull(tenant.getTenantId());
      assertNull(tenant.getName());
    }

    @Test
    void builderShouldProvideToString() {
      String str = Tenant.builder()
          .tenantId("test")
          .toString();
      assertNotNull(str);
    }
  }

  @Nested
  class TestCanEqual {

    @Test
    void canEqualShouldReturnTrueForSameType() {
      Tenant tenant1 = createFullTenant();
      Tenant tenant2 = createFullTenant();
      assertTrue(tenant1.canEqual(tenant2));
    }

    @Test
    void canEqualShouldReturnFalseForDifferentType() {
      Tenant tenant = createFullTenant();
      assertFalse(tenant.canEqual("string"));
      assertFalse(tenant.canEqual(123));
    }
  }

  @Nested
  class TestAllArgsConstructor {

    @Test
    void shouldCreateWithAllArgs() {
      Tenant tenant = new Tenant(
          "tenant", "name", "desc", true, "created", "updated", "gcp", "domain");
      
      assertEquals("tenant", tenant.getTenantId());
      assertEquals("name", tenant.getName());
      assertEquals("desc", tenant.getDescription());
      assertTrue(tenant.getIsActive());
      assertEquals("created", tenant.getCreatedAt());
      assertEquals("updated", tenant.getUpdatedAt());
      assertEquals("gcp", tenant.getGcpTenantId());
      assertEquals("domain", tenant.getDomainName());
    }

    @Test
    void shouldCreateWithNullArgs() {
      Tenant tenant = new Tenant(
          null, null, null, null, null, null, null, null);
      
      assertNull(tenant.getTenantId());
      assertNull(tenant.getName());
    }
  }

  private Tenant createFullTenant() {
    return Tenant.builder()
        .tenantId("tenant1")
        .name("Test Tenant")
        .description("Test Description")
        .isActive(true)
        .createdAt("2024-01-01")
        .updatedAt("2024-01-02")
        .gcpTenantId("gcp123")
        .domainName("example.com")
        .build();
  }
}
