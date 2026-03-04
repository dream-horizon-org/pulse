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

class ClickhouseCredentialsModelTest {

  @Nested
  class TestBuilder {

    @Test
    void shouldBuildWithAllFields() {
      ClickhouseCredentials credentials = ClickhouseCredentials.builder()
          .id(1L)
          .tenantId("test_tenant")
          .clickhouseUsername("user_test")
          .clickhousePassword("password123")
          .encryptionSalt("salt123")
          .passwordDigest("digest123")
          .isActive(true)
          .createdAt("2026-01-01T00:00:00")
          .updatedAt("2026-01-01T00:00:00")
          .build();

      assertEquals(1L, credentials.getId());
      assertEquals("test_tenant", credentials.getTenantId());
      assertEquals("user_test", credentials.getClickhouseUsername());
      assertEquals("password123", credentials.getClickhousePassword());
      assertEquals("salt123", credentials.getEncryptionSalt());
      assertEquals("digest123", credentials.getPasswordDigest());
      assertTrue(credentials.getIsActive());
      assertEquals("2026-01-01T00:00:00", credentials.getCreatedAt());
      assertEquals("2026-01-01T00:00:00", credentials.getUpdatedAt());
    }

    @Test
    void shouldBuildWithMinimalFields() {
      ClickhouseCredentials credentials = ClickhouseCredentials.builder()
          .tenantId("test_tenant")
          .build();

      assertEquals("test_tenant", credentials.getTenantId());
      assertNull(credentials.getId());
      assertNull(credentials.getClickhouseUsername());
    }
  }

  @Nested
  class TestSettersAndGetters {

    @Test
    void shouldSetAndGetAllFields() {
      ClickhouseCredentials credentials = new ClickhouseCredentials();

      credentials.setId(2L);
      credentials.setTenantId("tenant_abc");
      credentials.setClickhouseUsername("user_abc");
      credentials.setClickhousePassword("pass_abc");
      credentials.setEncryptionSalt("salt_abc");
      credentials.setPasswordDigest("digest_abc");
      credentials.setIsActive(false);
      credentials.setCreatedAt("2026-02-01T00:00:00");
      credentials.setUpdatedAt("2026-02-02T00:00:00");

      assertEquals(2L, credentials.getId());
      assertEquals("tenant_abc", credentials.getTenantId());
      assertEquals("user_abc", credentials.getClickhouseUsername());
      assertEquals("pass_abc", credentials.getClickhousePassword());
      assertEquals("salt_abc", credentials.getEncryptionSalt());
      assertEquals("digest_abc", credentials.getPasswordDigest());
      assertFalse(credentials.getIsActive());
      assertEquals("2026-02-01T00:00:00", credentials.getCreatedAt());
      assertEquals("2026-02-02T00:00:00", credentials.getUpdatedAt());
    }
  }

  @Nested
  class TestEqualsAndHashCode {

    @Test
    void shouldBeEqualForSameValues() {
      ClickhouseCredentials cred1 = ClickhouseCredentials.builder()
          .id(1L)
          .tenantId("test_tenant")
          .clickhouseUsername("user")
          .build();

      ClickhouseCredentials cred2 = ClickhouseCredentials.builder()
          .id(1L)
          .tenantId("test_tenant")
          .clickhouseUsername("user")
          .build();

      assertEquals(cred1, cred2);
      assertEquals(cred1.hashCode(), cred2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentValues() {
      ClickhouseCredentials cred1 = ClickhouseCredentials.builder()
          .id(1L)
          .tenantId("tenant_a")
          .build();

      ClickhouseCredentials cred2 = ClickhouseCredentials.builder()
          .id(2L)
          .tenantId("tenant_b")
          .build();

      assertNotEquals(cred1, cred2);
    }

    @Test
    void shouldBeEqualToItself() {
      ClickhouseCredentials cred = ClickhouseCredentials.builder()
          .tenantId("test")
          .build();

      assertEquals(cred, cred);
    }

    @Test
    void shouldNotBeEqualToNull() {
      ClickhouseCredentials cred = ClickhouseCredentials.builder()
          .tenantId("test")
          .build();

      assertNotEquals(null, cred);
    }

    @Test
    void shouldNotBeEqualToDifferentType() {
      ClickhouseCredentials cred = ClickhouseCredentials.builder()
          .tenantId("test")
          .build();

      assertNotEquals("string", cred);
    }
  }

  @Nested
  class TestToString {

    @Test
    void shouldGenerateToString() {
      ClickhouseCredentials credentials = ClickhouseCredentials.builder()
          .id(1L)
          .tenantId("test_tenant")
          .clickhouseUsername("user")
          .isActive(true)
          .build();

      String toString = credentials.toString();

      assertNotNull(toString);
      assertTrue(toString.contains("test_tenant"));
      assertTrue(toString.contains("user"));
    }
  }

  @Nested
  class TestAllArgsConstructor {

    @Test
    void shouldCreateWithAllArgsConstructor() {
      ClickhouseCredentials credentials = new ClickhouseCredentials(
          1L, "tenant", "user", "pass", "salt", "digest", true, "created", "updated"
      );

      assertEquals(1L, credentials.getId());
      assertEquals("tenant", credentials.getTenantId());
      assertEquals("user", credentials.getClickhouseUsername());
      assertEquals("pass", credentials.getClickhousePassword());
      assertEquals("salt", credentials.getEncryptionSalt());
      assertEquals("digest", credentials.getPasswordDigest());
      assertTrue(credentials.getIsActive());
      assertEquals("created", credentials.getCreatedAt());
      assertEquals("updated", credentials.getUpdatedAt());
    }
  }

  @Nested
  class TestNoArgsConstructor {

    @Test
    void shouldCreateWithNoArgsConstructor() {
      ClickhouseCredentials credentials = new ClickhouseCredentials();

      assertNull(credentials.getId());
      assertNull(credentials.getTenantId());
      assertNull(credentials.getClickhouseUsername());
    }
  }
}
