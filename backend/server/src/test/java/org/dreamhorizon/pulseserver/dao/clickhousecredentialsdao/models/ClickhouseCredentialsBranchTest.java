package org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseCredentials;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for ClickhouseCredentials model to cover all Lombok branches.
 */
class ClickhouseCredentialsBranchTest {

  @Nested
  class TestEqualsAllBranches {

    @Test
    void equalsShouldReturnTrueForSameObject() {
      ClickhouseCredentials cred = createFullCredentials();
      assertTrue(cred.equals(cred));
    }

    @Test
    void equalsShouldReturnFalseForNull() {
      ClickhouseCredentials cred = createFullCredentials();
      assertFalse(cred.equals(null));
    }

    @Test
    void equalsShouldReturnFalseForDifferentClass() {
      ClickhouseCredentials cred = createFullCredentials();
      assertFalse(cred.equals("not a credential"));
      assertFalse(cred.equals(Integer.valueOf(1)));
      assertFalse(cred.equals(new Object()));
    }

    @Test
    void equalsShouldReturnTrueForEqualObjects() {
      ClickhouseCredentials cred1 = createFullCredentials();
      ClickhouseCredentials cred2 = createFullCredentials();
      assertTrue(cred1.equals(cred2));
      assertTrue(cred2.equals(cred1));
    }

    @Test
    void equalsShouldReturnFalseWhenTenantIdDiffers() {
      ClickhouseCredentials cred1 = createFullCredentials();
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setTenantId("different");
      assertFalse(cred1.equals(cred2));
    }

    @Test
    void equalsShouldReturnFalseWhenUsernameDiffers() {
      ClickhouseCredentials cred1 = createFullCredentials();
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setClickhouseUsername("different");
      assertFalse(cred1.equals(cred2));
    }

    @Test
    void equalsShouldIgnoreTransientPasswordField() {
      // clickhousePassword is transient, so it's excluded from equals
      ClickhouseCredentials cred1 = createFullCredentials();
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setClickhousePassword("different");
      // Transient fields are not included in @Data equals/hashCode
      assertTrue(cred1.equals(cred2));
    }

    @Test
    void equalsShouldReturnFalseWhenDigestDiffers() {
      ClickhouseCredentials cred1 = createFullCredentials();
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setPasswordDigest("different");
      assertFalse(cred1.equals(cred2));
    }

    @Test
    void equalsShouldReturnFalseWhenIsActiveDiffers() {
      ClickhouseCredentials cred1 = createFullCredentials();
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setIsActive(false);
      assertFalse(cred1.equals(cred2));
    }

    @Test
    void equalsShouldReturnFalseWhenCreatedAtDiffers() {
      ClickhouseCredentials cred1 = createFullCredentials();
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setCreatedAt("different");
      assertFalse(cred1.equals(cred2));
    }

    @Test
    void equalsShouldReturnFalseWhenUpdatedAtDiffers() {
      ClickhouseCredentials cred1 = createFullCredentials();
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setUpdatedAt("different");
      assertFalse(cred1.equals(cred2));
    }

    @Test
    void equalsShouldHandleNullTenantId() {
      ClickhouseCredentials cred1 = createFullCredentials();
      cred1.setTenantId(null);
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setTenantId(null);
      assertTrue(cred1.equals(cred2));

      ClickhouseCredentials cred3 = createFullCredentials();
      assertFalse(cred1.equals(cred3));
      assertFalse(cred3.equals(cred1));
    }

    @Test
    void equalsShouldHandleNullUsername() {
      ClickhouseCredentials cred1 = createFullCredentials();
      cred1.setClickhouseUsername(null);
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setClickhouseUsername(null);
      assertTrue(cred1.equals(cred2));

      ClickhouseCredentials cred3 = createFullCredentials();
      assertFalse(cred1.equals(cred3));
    }

    @Test
    void equalsShouldIgnoreNullTransientPasswordField() {
      // clickhousePassword is transient, so it's excluded from equals
      ClickhouseCredentials cred1 = createFullCredentials();
      cred1.setClickhousePassword(null);
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setClickhousePassword(null);
      assertTrue(cred1.equals(cred2));

      ClickhouseCredentials cred3 = createFullCredentials();
      // Since password is transient, both are still equal
      assertTrue(cred1.equals(cred3));
    }

    @Test
    void equalsShouldHandleNullDigest() {
      ClickhouseCredentials cred1 = createFullCredentials();
      cred1.setPasswordDigest(null);
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setPasswordDigest(null);
      assertTrue(cred1.equals(cred2));
    }

    @Test
    void equalsShouldHandleNullIsActive() {
      ClickhouseCredentials cred1 = createFullCredentials();
      cred1.setIsActive(null);
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setIsActive(null);
      assertTrue(cred1.equals(cred2));
    }

    @Test
    void equalsShouldHandleNullCreatedAt() {
      ClickhouseCredentials cred1 = createFullCredentials();
      cred1.setCreatedAt(null);
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setCreatedAt(null);
      assertTrue(cred1.equals(cred2));
    }

    @Test
    void equalsShouldHandleNullUpdatedAt() {
      ClickhouseCredentials cred1 = createFullCredentials();
      cred1.setUpdatedAt(null);
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setUpdatedAt(null);
      assertTrue(cred1.equals(cred2));
    }

    @Test
    void equalsShouldHandleAllNullFields() {
      ClickhouseCredentials cred1 = new ClickhouseCredentials();
      ClickhouseCredentials cred2 = new ClickhouseCredentials();
      assertTrue(cred1.equals(cred2));
    }
  }

  @Nested
  class TestHashCodeAllBranches {

    @Test
    void hashCodeShouldBeConsistent() {
      ClickhouseCredentials cred = createFullCredentials();
      int hash1 = cred.hashCode();
      int hash2 = cred.hashCode();
      assertEquals(hash1, hash2);
    }

    @Test
    void hashCodeShouldBeEqualForEqualObjects() {
      ClickhouseCredentials cred1 = createFullCredentials();
      ClickhouseCredentials cred2 = createFullCredentials();
      assertEquals(cred1.hashCode(), cred2.hashCode());
    }

    @Test
    void hashCodeShouldDifferForDifferentObjects() {
      ClickhouseCredentials cred1 = createFullCredentials();
      ClickhouseCredentials cred2 = createFullCredentials();
      cred2.setTenantId("different");
      assertNotEquals(cred1.hashCode(), cred2.hashCode());
    }

    @Test
    void hashCodeShouldHandleNullFields() {
      ClickhouseCredentials cred = new ClickhouseCredentials();
      int hash = cred.hashCode();
      assertNotNull(hash);
    }

    @Test
    void hashCodeShouldHandlePartialNullFields() {
      ClickhouseCredentials cred = createFullCredentials();
      cred.setTenantId(null);
      int hash = cred.hashCode();
      assertNotNull(hash);
    }
  }

  @Nested
  class TestToStringAllBranches {

    @Test
    void toStringShouldNotReturnNull() {
      ClickhouseCredentials cred = createFullCredentials();
      assertNotNull(cred.toString());
    }

    @Test
    void toStringShouldContainFieldNames() {
      ClickhouseCredentials cred = createFullCredentials();
      String str = cred.toString();
      assertTrue(str.contains("tenantId"));
      assertTrue(str.contains("clickhouseUsername"));
      assertTrue(str.contains("isActive"));
    }

    @Test
    void toStringShouldHandleNullFields() {
      ClickhouseCredentials cred = new ClickhouseCredentials();
      String str = cred.toString();
      assertNotNull(str);
    }
  }

  @Nested
  class TestBuilderAllBranches {

    @Test
    void builderShouldCreateObjectWithAllFields() {
      ClickhouseCredentials cred = ClickhouseCredentials.builder()
          .tenantId("tenant1")
          .clickhouseUsername("user1")
          .clickhousePassword("pass1")
          .passwordDigest("digest1")
          .isActive(true)
          .createdAt("2024-01-01")
          .updatedAt("2024-01-02")
          .build();

      assertEquals("tenant1", cred.getTenantId());
      assertEquals("user1", cred.getClickhouseUsername());
      assertEquals("pass1", cred.getClickhousePassword());
      assertEquals("digest1", cred.getPasswordDigest());
      assertTrue(cred.getIsActive());
      assertEquals("2024-01-01", cred.getCreatedAt());
      assertEquals("2024-01-02", cred.getUpdatedAt());
    }

    @Test
    void builderShouldHandleNullValues() {
      ClickhouseCredentials cred = ClickhouseCredentials.builder()
          .tenantId(null)
          .clickhouseUsername(null)
          .clickhousePassword(null)
          .passwordDigest(null)
          .isActive(null)
          .createdAt(null)
          .updatedAt(null)
          .build();

      assertNull(cred.getTenantId());
      assertNull(cred.getClickhouseUsername());
    }

    @Test
    void builderShouldProvideToString() {
      String str = ClickhouseCredentials.builder()
          .tenantId("test")
          .toString();
      assertNotNull(str);
    }
  }

  @Nested
  class TestCanEqual {

    @Test
    void canEqualShouldReturnTrueForSameType() {
      ClickhouseCredentials cred1 = createFullCredentials();
      ClickhouseCredentials cred2 = createFullCredentials();
      assertEquals(cred1, cred2);
    }

    @Test
    void canEqualShouldReturnFalseForDifferentType() {
      ClickhouseCredentials cred = createFullCredentials();
      assertNotEquals("string", cred);
      assertNotEquals(123, cred);
    }
  }

  @Nested
  class TestGettersAndSetters {

    @Test
    void shouldSetAndGetAllFields() {
      ClickhouseCredentials cred = new ClickhouseCredentials();

      cred.setId(1L);
      cred.setTenantId("t1");
      cred.setClickhouseUsername("u1");
      cred.setClickhousePassword("p1");
      cred.setEncryptionSalt("s1");
      cred.setPasswordDigest("d1");
      cred.setIsActive(true);
      cred.setCreatedAt("c1");
      cred.setUpdatedAt("upd1");

      assertEquals(1L, cred.getId());
      assertEquals("t1", cred.getTenantId());
      assertEquals("u1", cred.getClickhouseUsername());
      assertEquals("p1", cred.getClickhousePassword());
      assertEquals("s1", cred.getEncryptionSalt());
      assertEquals("d1", cred.getPasswordDigest());
      assertTrue(cred.getIsActive());
      assertEquals("c1", cred.getCreatedAt());
      assertEquals("upd1", cred.getUpdatedAt());
    }
  }

  @Nested
  class TestAllArgsConstructor {

    @Test
    void shouldCreateWithAllArgs() {
      ClickhouseCredentials cred = new ClickhouseCredentials(
          1L, "tenant", "user", "pass", "salt", "digest", true, "created", "updated");

      assertEquals(1L, cred.getId());
      assertEquals("tenant", cred.getTenantId());
      assertEquals("user", cred.getClickhouseUsername());
      assertEquals("pass", cred.getClickhousePassword());
      assertEquals("salt", cred.getEncryptionSalt());
      assertEquals("digest", cred.getPasswordDigest());
      assertTrue(cred.getIsActive());
      assertEquals("created", cred.getCreatedAt());
      assertEquals("updated", cred.getUpdatedAt());
    }

    @Test
    void shouldCreateWithNullArgs() {
      ClickhouseCredentials cred = new ClickhouseCredentials(
          null, null, null, null, null, null, null, null, null);

      assertNull(cred.getTenantId());
      assertNull(cred.getClickhouseUsername());
    }
  }

  private ClickhouseCredentials createFullCredentials() {
    return ClickhouseCredentials.builder()
        .id(1L)
        .tenantId("tenant1")
        .clickhouseUsername("user1")
        .clickhousePassword("pass1")
        .encryptionSalt("salt1")
        .passwordDigest("digest1")
        .isActive(true)
        .createdAt("2024-01-01")
        .updatedAt("2024-01-02")
        .build();
  }
}
