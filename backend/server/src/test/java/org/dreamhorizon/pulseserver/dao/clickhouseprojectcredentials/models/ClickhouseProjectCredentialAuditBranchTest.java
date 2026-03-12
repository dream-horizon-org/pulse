package org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.models.ClickhouseProjectCredentialAudit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for ClickhouseProjectCredentialAudit model to cover all Lombok branches.
 */
class ClickhouseProjectCredentialAuditBranchTest {

  @Nested
  class TestEqualsAllBranches {

    @Test
    void equalsShouldReturnTrueForSameObject() {
      ClickhouseProjectCredentialAudit audit = createFullAudit();
      assertTrue(audit.equals(audit));
    }

    @Test
    void equalsShouldReturnFalseForNull() {
      ClickhouseProjectCredentialAudit audit = createFullAudit();
      assertFalse(audit.equals(null));
    }

    @Test
    void equalsShouldReturnFalseForDifferentClass() {
      ClickhouseProjectCredentialAudit audit = createFullAudit();
      assertFalse(audit.equals("not an audit"));
      assertFalse(audit.equals(Integer.valueOf(1)));
    }

    @Test
    void equalsShouldReturnTrueForEqualObjects() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      assertTrue(audit1.equals(audit2));
      assertTrue(audit2.equals(audit1));
    }

    @Test
    void equalsShouldReturnFalseWhenAuditIdDiffers() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setId(999L);
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldReturnFalseWhenProjectIdDiffers() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setProjectId("different");
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldReturnFalseWhenActionDiffers() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setAction("DIFFERENT_ACTION");
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldReturnFalseWhenPerformedByDiffers() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setPerformedBy("different_user");
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldReturnFalseWhenDetailsDiffers() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setDetails("different_details");
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldReturnFalseWhenCreatedAtDiffers() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setCreatedAt("different_date");
      assertFalse(audit1.equals(audit2));
    }

    @Test
    void equalsShouldHandleNullAuditId() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      audit1.setId(null);
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setId(null);
      assertTrue(audit1.equals(audit2));

      ClickhouseProjectCredentialAudit audit3 = createFullAudit();
      assertFalse(audit1.equals(audit3));
    }

    @Test
    void equalsShouldHandleNullProjectId() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      audit1.setProjectId(null);
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setProjectId(null);
      assertTrue(audit1.equals(audit2));

      ClickhouseProjectCredentialAudit audit3 = createFullAudit();
      assertFalse(audit1.equals(audit3));
    }

    @Test
    void equalsShouldHandleNullAction() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      audit1.setAction(null);
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setAction(null);
      assertTrue(audit1.equals(audit2));
    }

    @Test
    void equalsShouldHandleNullPerformedBy() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      audit1.setPerformedBy(null);
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setPerformedBy(null);
      assertTrue(audit1.equals(audit2));
    }

    @Test
    void equalsShouldHandleNullDetails() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      audit1.setDetails(null);
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setDetails(null);
      assertTrue(audit1.equals(audit2));
    }

    @Test
    void equalsShouldHandleNullCreatedAt() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      audit1.setCreatedAt(null);
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setCreatedAt(null);
      assertTrue(audit1.equals(audit2));
    }

    @Test
    void equalsShouldHandleAllNullFields() {
      ClickhouseProjectCredentialAudit audit1 = new ClickhouseProjectCredentialAudit();
      ClickhouseProjectCredentialAudit audit2 = new ClickhouseProjectCredentialAudit();
      assertTrue(audit1.equals(audit2));
    }
  }

  @Nested
  class TestHashCodeAllBranches {

    @Test
    void hashCodeShouldBeConsistent() {
      ClickhouseProjectCredentialAudit audit = createFullAudit();
      int hash1 = audit.hashCode();
      int hash2 = audit.hashCode();
      assertEquals(hash1, hash2);
    }

    @Test
    void hashCodeShouldBeEqualForEqualObjects() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      assertEquals(audit1.hashCode(), audit2.hashCode());
    }

    @Test
    void hashCodeShouldDifferForDifferentObjects() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      audit2.setId(999L);
      assertNotEquals(audit1.hashCode(), audit2.hashCode());
    }

    @Test
    void hashCodeShouldHandleNullFields() {
      ClickhouseProjectCredentialAudit audit = new ClickhouseProjectCredentialAudit();
      int hash = audit.hashCode();
      assertNotNull(hash);
    }
  }

  @Nested
  class TestToStringAllBranches {

    @Test
    void toStringShouldNotReturnNull() {
      ClickhouseProjectCredentialAudit audit = createFullAudit();
      assertNotNull(audit.toString());
    }

    @Test
    void toStringShouldContainFieldNames() {
      ClickhouseProjectCredentialAudit audit = createFullAudit();
      String str = audit.toString();
      assertTrue(str.contains("id"));
      assertTrue(str.contains("projectId"));
      assertTrue(str.contains("action"));
    }

    @Test
    void toStringShouldHandleNullFields() {
      ClickhouseProjectCredentialAudit audit = new ClickhouseProjectCredentialAudit();
      String str = audit.toString();
      assertNotNull(str);
    }
  }

  @Nested
  class TestBuilderAllBranches {

    @Test
    void builderShouldCreateObjectWithAllFields() {
      ClickhouseProjectCredentialAudit audit = ClickhouseProjectCredentialAudit.builder()
          .id(1L)
          .projectId("project1")
          .action("CREATE")
          .performedBy("admin")
          .details("test_details")
          .createdAt("2024-01-01")
          .build();

      assertEquals(1L, audit.getId());
      assertEquals("project1", audit.getProjectId());
      assertEquals("CREATE", audit.getAction());
      assertEquals("admin", audit.getPerformedBy());
      assertEquals("test_details", audit.getDetails());
      assertEquals("2024-01-01", audit.getCreatedAt());
    }

    @Test
    void builderShouldHandleNullValues() {
      ClickhouseProjectCredentialAudit audit = ClickhouseProjectCredentialAudit.builder()
          .id(null)
          .projectId(null)
          .action(null)
          .performedBy(null)
          .details(null)
          .createdAt(null)
          .build();

      assertNull(audit.getId());
      assertNull(audit.getProjectId());
    }

    @Test
    void builderShouldProvideToString() {
      String str = ClickhouseProjectCredentialAudit.builder()
          .id(1L)
          .toString();
      assertNotNull(str);
    }
  }

  @Nested
  class TestCanEqual {

    @Test
    void canEqualShouldReturnTrueForSameType() {
      ClickhouseProjectCredentialAudit audit1 = createFullAudit();
      ClickhouseProjectCredentialAudit audit2 = createFullAudit();
      assertTrue(audit1.equals(audit2));
    }

    @Test
    void canEqualShouldReturnFalseForDifferentType() {
      ClickhouseProjectCredentialAudit audit = createFullAudit();
      assertFalse(audit.equals("string"));
      assertFalse(audit.equals(123));
    }
  }

  @Nested
  class TestAllArgsConstructor {

    @Test
    void shouldCreateWithAllArgs() {
      ClickhouseProjectCredentialAudit audit = new ClickhouseProjectCredentialAudit(
          1L, "project", "action", "performer", "details", "created");

      assertEquals(1L, audit.getId());
      assertEquals("project", audit.getProjectId());
      assertEquals("action", audit.getAction());
      assertEquals("performer", audit.getPerformedBy());
      assertEquals("details", audit.getDetails());
      assertEquals("created", audit.getCreatedAt());
    }
  }

  private ClickhouseProjectCredentialAudit createFullAudit() {
    return ClickhouseProjectCredentialAudit.builder()
        .id(1L)
        .projectId("project1")
        .action("CREATE")
        .performedBy("admin")
        .details("test_details")
        .createdAt("2024-01-01")
        .build();
  }
}
