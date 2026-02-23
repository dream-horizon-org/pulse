package org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for ClickhouseTenantCredentialAudit model to cover all Lombok branches.
 */
class ClickhouseTenantCredentialAuditBranchTest {

  @Nested
  class TestEqualsAllBranches {

    @Test
    void equalsShouldReturnTrueForSameObject() {
      ClickhouseTenantCredentialAudit audit = createFullAudit();
      assertTrue(audit.equals(audit));
    }

    @Test
    void equalsShouldReturnFalseForNull() {
      ClickhouseTenantCredentialAudit audit = createFullAudit();
      assertFalse(audit.equals(null));
    }

    @Test
    void equalsShouldReturnFalseForDifferentClass() {
      ClickhouseTenantCredentialAudit audit = createFullAudit();
      assertFalse(audit.equals("not an audit"));
      assertFalse(audit.equals(Integer.valueOf(1)));
    }

    @Test
    void equalsShouldReturnTrueForEqualObjects() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      assertTrue(audit1.equals(audit2));
      assertTrue(audit2.equals(audit1));
    }

    @Test
    void equalsShouldReturnFalseWhenAuditIdDiffers() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setId(999L);
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldReturnFalseWhenTenantIdDiffers() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setTenantId("different");
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldReturnFalseWhenActionDiffers() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setAction("DIFFERENT_ACTION");
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldReturnFalseWhenPerformedByDiffers() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setPerformedBy("different_user");
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldReturnFalseWhenDetailsDiffers() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setDetails("different_details");
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldReturnFalseWhenCreatedAtDiffers() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setCreatedAt("different_date");
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldHandleNullAuditId() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      audit1.setId(null);
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setId(null);
      assertTrue(audit1.equals(audit2));
      
      ClickhouseTenantCredentialAudit audit3 = createFullAudit();
      assertFalse(audit1.equals(audit3));
    }

    @Test
    void equalsShouldHandleNullTenantId() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      audit1.setTenantId(null);
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setTenantId(null);
      assertTrue(audit1.equals(audit2));
      
      ClickhouseTenantCredentialAudit audit3 = createFullAudit();
      assertFalse(audit1.equals(audit3));
    }

    @Test
    void equalsShouldHandleNullAction() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      audit1.setAction(null);
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setAction(null);
      assertTrue(audit1.equals(audit2));
    }

    @Test
    void equalsShouldHandleNullPerformedBy() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      audit1.setPerformedBy(null);
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setPerformedBy(null);
      assertTrue(audit1.equals(audit2));
    }

    @Test
    void equalsShouldHandleNullDetails() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      audit1.setDetails(null);
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setDetails(null);
      assertTrue(audit1.equals(audit2));
    }

    @Test
    void equalsShouldHandleNullCreatedAt() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      audit1.setCreatedAt(null);
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setCreatedAt(null);
      assertTrue(audit1.equals(audit2));
    }

    @Test
    void equalsShouldHandleAllNullFields() {
      ClickhouseTenantCredentialAudit audit1 = new ClickhouseTenantCredentialAudit();
      ClickhouseTenantCredentialAudit audit2 = new ClickhouseTenantCredentialAudit();
      assertTrue(audit1.equals(audit2));
    }
  }

  @Nested
  class TestHashCodeAllBranches {

    @Test
    void hashCodeShouldBeConsistent() {
      ClickhouseTenantCredentialAudit audit = createFullAudit();
      int hash1 = audit.hashCode();
      int hash2 = audit.hashCode();
      assertEquals(hash1, hash2);
    }

    @Test
    void hashCodeShouldBeEqualForEqualObjects() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      assertEquals(audit1.hashCode(), audit2.hashCode());
    }

    @Test
    void hashCodeShouldDifferForDifferentObjects() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      audit2.setId(999L);
      assertNotEquals(audit1.hashCode(), audit2.hashCode());
    }

    @Test
    void hashCodeShouldHandleNullFields() {
      ClickhouseTenantCredentialAudit audit = new ClickhouseTenantCredentialAudit();
      int hash = audit.hashCode();
      assertNotNull(hash);
    }
  }

  @Nested
  class TestToStringAllBranches {

    @Test
    void toStringShouldNotReturnNull() {
      ClickhouseTenantCredentialAudit audit = createFullAudit();
      assertNotNull(audit.toString());
    }

    @Test
    void toStringShouldContainFieldNames() {
      ClickhouseTenantCredentialAudit audit = createFullAudit();
      String str = audit.toString();
      assertTrue(str.contains("id"));
      assertTrue(str.contains("tenantId"));
      assertTrue(str.contains("action"));
    }

    @Test
    void toStringShouldHandleNullFields() {
      ClickhouseTenantCredentialAudit audit = new ClickhouseTenantCredentialAudit();
      String str = audit.toString();
      assertNotNull(str);
    }
  }

  @Nested
  class TestBuilderAllBranches {

    @Test
    void builderShouldCreateObjectWithAllFields() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .id(1L)
          .tenantId("tenant1")
          .action("CREATE")
          .performedBy("admin")
          .details("test_details")
          .createdAt("2024-01-01")
          .build();
      
      assertEquals(1L, audit.getId());
      assertEquals("tenant1", audit.getTenantId());
      assertEquals("CREATE", audit.getAction());
      assertEquals("admin", audit.getPerformedBy());
      assertEquals("test_details", audit.getDetails());
      assertEquals("2024-01-01", audit.getCreatedAt());
    }

    @Test
    void builderShouldHandleNullValues() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .id(null)
          .tenantId(null)
          .action(null)
          .performedBy(null)
          .details(null)
          .createdAt(null)
          .build();
      
      assertNull(audit.getId());
      assertNull(audit.getTenantId());
    }

    @Test
    void builderShouldProvideToString() {
      String str = ClickhouseTenantCredentialAudit.builder()
          .id(1L)
          .toString();
      assertNotNull(str);
    }
  }

  @Nested
  class TestCanEqual {

    @Test
    void canEqualShouldReturnTrueForSameType() {
      ClickhouseTenantCredentialAudit audit1 = createFullAudit();
      ClickhouseTenantCredentialAudit audit2 = createFullAudit();
      assertTrue(audit1.canEqual(audit2));
    }

    @Test
    void canEqualShouldReturnFalseForDifferentType() {
      ClickhouseTenantCredentialAudit audit = createFullAudit();
      assertFalse(audit.canEqual("string"));
      assertFalse(audit.canEqual(123));
    }
  }

  @Nested
  class TestAllArgsConstructor {

    @Test
    void shouldCreateWithAllArgs() {
      ClickhouseTenantCredentialAudit audit = new ClickhouseTenantCredentialAudit(
          1L, "tenant", "action", "performer", "details", "created");
      
      assertEquals(1L, audit.getId());
      assertEquals("tenant", audit.getTenantId());
      assertEquals("action", audit.getAction());
      assertEquals("performer", audit.getPerformedBy());
      assertEquals("details", audit.getDetails());
      assertEquals("created", audit.getCreatedAt());
    }
  }

  private ClickhouseTenantCredentialAudit createFullAudit() {
    return ClickhouseTenantCredentialAudit.builder()
        .id(1L)
        .tenantId("tenant1")
        .action("CREATE")
        .performedBy("admin")
        .details("test_details")
        .createdAt("2024-01-01")
        .build();
  }
}
