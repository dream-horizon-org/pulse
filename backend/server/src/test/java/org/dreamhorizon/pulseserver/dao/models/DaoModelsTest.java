package org.dreamhorizon.pulseserver.dao.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models.ClickhouseTenantCredentialAudit;
import org.dreamhorizon.pulseserver.dao.tenantdao.models.Tenant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DaoModelsTest {

  @Nested
  class TestClickhouseCredentials {

    @Test
    void shouldBuildWithAllFields() {
      ClickhouseCredentials creds = ClickhouseCredentials.builder()
          .id(1L)
          .tenantId("tenant_1")
          .clickhouseUsername("ch_user")
          .clickhousePassword("ch_pass")
          .encryptionSalt("salt")
          .passwordDigest("digest")
          .isActive(true)
          .createdAt("2024-01-01")
          .updatedAt("2024-01-02")
          .build();

      assertEquals(1L, creds.getId());
      assertEquals("tenant_1", creds.getTenantId());
      assertEquals("ch_user", creds.getClickhouseUsername());
      assertEquals("ch_pass", creds.getClickhousePassword());
      assertEquals("salt", creds.getEncryptionSalt());
      assertEquals("digest", creds.getPasswordDigest());
      assertTrue(creds.getIsActive());
      assertEquals("2024-01-01", creds.getCreatedAt());
      assertEquals("2024-01-02", creds.getUpdatedAt());
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      ClickhouseCredentials creds = new ClickhouseCredentials();
      assertNull(creds.getId());
      assertNull(creds.getTenantId());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      ClickhouseCredentials creds = new ClickhouseCredentials(
          1L, "t1", "user", "pass", "salt", "digest", true, "created", "updated"
      );
      assertEquals(1L, creds.getId());
      assertEquals("t1", creds.getTenantId());
      assertEquals("user", creds.getClickhouseUsername());
    }

    @Test
    void shouldSetAndGetAllProperties() {
      ClickhouseCredentials creds = new ClickhouseCredentials();
      creds.setId(1L);
      creds.setTenantId("t1");
      creds.setClickhouseUsername("user");
      creds.setClickhousePassword("pass");
      creds.setEncryptionSalt("salt");
      creds.setPasswordDigest("digest");
      creds.setIsActive(false);
      creds.setCreatedAt("created");
      creds.setUpdatedAt("updated");

      assertEquals(1L, creds.getId());
      assertEquals("t1", creds.getTenantId());
      assertEquals("user", creds.getClickhouseUsername());
      assertEquals("pass", creds.getClickhousePassword());
      assertEquals("salt", creds.getEncryptionSalt());
      assertEquals("digest", creds.getPasswordDigest());
      assertFalse(creds.getIsActive());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
      ClickhouseCredentials c1 = ClickhouseCredentials.builder().id(1L).tenantId("t1").build();
      ClickhouseCredentials c2 = ClickhouseCredentials.builder().id(1L).tenantId("t1").build();
      ClickhouseCredentials c3 = ClickhouseCredentials.builder().id(2L).tenantId("t1").build();

      assertEquals(c1, c2);
      assertNotEquals(c1, c3);
      assertEquals(c1, c1);
      assertNotEquals(c1, null);
      assertNotEquals(c1, "string");
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
      ClickhouseCredentials c1 = ClickhouseCredentials.builder().id(1L).tenantId("t1").build();
      ClickhouseCredentials c2 = ClickhouseCredentials.builder().id(1L).tenantId("t1").build();
      assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void shouldImplementToString() {
      ClickhouseCredentials creds = ClickhouseCredentials.builder().tenantId("t1").build();
      String str = creds.toString();
      assertNotNull(str);
      assertTrue(str.contains("t1") || str.contains("tenantId"));
    }

    @Test
    void shouldHandleEqualsWithDifferentFields() {
      ClickhouseCredentials base = ClickhouseCredentials.builder()
          .id(1L).tenantId("t1").isActive(true).build();
      
      assertNotEquals(base, ClickhouseCredentials.builder()
          .id(2L).tenantId("t1").isActive(true).build());
      assertNotEquals(base, ClickhouseCredentials.builder()
          .id(1L).tenantId("t2").isActive(true).build());
      assertNotEquals(base, ClickhouseCredentials.builder()
          .id(1L).tenantId("t1").isActive(false).build());
    }

    @Test
    void shouldHandleNullFieldsInEquals() {
      ClickhouseCredentials c1 = ClickhouseCredentials.builder().build();
      ClickhouseCredentials c2 = ClickhouseCredentials.builder().build();
      assertEquals(c1, c2);
    }

    @Test
    void shouldHandleEqualsWithNullVsNonNull() {
      ClickhouseCredentials c1 = ClickhouseCredentials.builder().tenantId(null).build();
      ClickhouseCredentials c2 = ClickhouseCredentials.builder().tenantId("t1").build();
      assertNotEquals(c1, c2);
      assertNotEquals(c2, c1);
    }

    @Test
    void shouldHandleHashCodeWithAllNulls() {
      ClickhouseCredentials c = new ClickhouseCredentials();
      assertNotNull(c.hashCode());
    }

    @Test
    void shouldHandleEqualsWithUsernameDiff() {
      ClickhouseCredentials c1 = ClickhouseCredentials.builder().clickhouseUsername("u1").build();
      ClickhouseCredentials c2 = ClickhouseCredentials.builder().clickhouseUsername("u2").build();
      assertNotEquals(c1, c2);
    }

    @Test
    void shouldHandleEqualsWithPasswordIgnoredAsTransient() {
      // clickhousePassword is transient, so it's excluded from equals/hashCode
      ClickhouseCredentials c1 = ClickhouseCredentials.builder().clickhousePassword("p1").build();
      ClickhouseCredentials c2 = ClickhouseCredentials.builder().clickhousePassword("p2").build();
      // Transient fields are ignored in equals, so these should be equal
      assertEquals(c1, c2);
    }

    @Test
    void shouldHandleEqualsWithSaltDiff() {
      ClickhouseCredentials c1 = ClickhouseCredentials.builder().encryptionSalt("s1").build();
      ClickhouseCredentials c2 = ClickhouseCredentials.builder().encryptionSalt("s2").build();
      assertNotEquals(c1, c2);
    }

    @Test
    void shouldHandleEqualsWithDigestDiff() {
      ClickhouseCredentials c1 = ClickhouseCredentials.builder().passwordDigest("d1").build();
      ClickhouseCredentials c2 = ClickhouseCredentials.builder().passwordDigest("d2").build();
      assertNotEquals(c1, c2);
    }

    @Test
    void shouldHandleEqualsWithCreatedAtDiff() {
      ClickhouseCredentials c1 = ClickhouseCredentials.builder().createdAt("2024-01-01").build();
      ClickhouseCredentials c2 = ClickhouseCredentials.builder().createdAt("2024-01-02").build();
      assertNotEquals(c1, c2);
    }

    @Test
    void shouldHandleEqualsWithUpdatedAtDiff() {
      ClickhouseCredentials c1 = ClickhouseCredentials.builder().updatedAt("2024-01-01").build();
      ClickhouseCredentials c2 = ClickhouseCredentials.builder().updatedAt("2024-01-02").build();
      assertNotEquals(c1, c2);
    }
  }

  @Nested
  class TestClickhouseTenantCredentialAudit {

    @Test
    void shouldBuildWithAllFields() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .id(1L)
          .tenantId("tenant_1")
          .action("CREATED")
          .performedBy("admin")
          .details("Details here")
          .createdAt("2024-01-01")
          .build();

      assertEquals(1L, audit.getId());
      assertEquals("tenant_1", audit.getTenantId());
      assertEquals("CREATED", audit.getAction());
      assertEquals("admin", audit.getPerformedBy());
      assertEquals("Details here", audit.getDetails());
      assertEquals("2024-01-01", audit.getCreatedAt());
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      ClickhouseTenantCredentialAudit audit = new ClickhouseTenantCredentialAudit();
      assertNull(audit.getId());
      assertNull(audit.getTenantId());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      ClickhouseTenantCredentialAudit audit = new ClickhouseTenantCredentialAudit(
          1L, "t1", "CREATED", "admin", "details", "2024-01-01"
      );
      assertEquals(1L, audit.getId());
      assertEquals("t1", audit.getTenantId());
      assertEquals("CREATED", audit.getAction());
    }

    @Test
    void shouldSetAndGetAllProperties() {
      ClickhouseTenantCredentialAudit audit = new ClickhouseTenantCredentialAudit();
      audit.setId(1L);
      audit.setTenantId("t1");
      audit.setAction("UPDATED");
      audit.setPerformedBy("system");
      audit.setDetails("some details");
      audit.setCreatedAt("2024-01-01");

      assertEquals(1L, audit.getId());
      assertEquals("t1", audit.getTenantId());
      assertEquals("UPDATED", audit.getAction());
      assertEquals("system", audit.getPerformedBy());
      assertEquals("some details", audit.getDetails());
      assertEquals("2024-01-01", audit.getCreatedAt());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
      ClickhouseTenantCredentialAudit a1 = ClickhouseTenantCredentialAudit.builder()
          .id(1L).tenantId("t1").build();
      ClickhouseTenantCredentialAudit a2 = ClickhouseTenantCredentialAudit.builder()
          .id(1L).tenantId("t1").build();
      ClickhouseTenantCredentialAudit a3 = ClickhouseTenantCredentialAudit.builder()
          .id(2L).tenantId("t1").build();

      assertEquals(a1, a2);
      assertNotEquals(a1, a3);
      assertEquals(a1, a1);
      assertNotEquals(a1, null);
      assertNotEquals(a1, "string");
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
      ClickhouseTenantCredentialAudit a1 = ClickhouseTenantCredentialAudit.builder()
          .id(1L).tenantId("t1").build();
      ClickhouseTenantCredentialAudit a2 = ClickhouseTenantCredentialAudit.builder()
          .id(1L).tenantId("t1").build();
      assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    void shouldImplementToString() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .tenantId("t1").action("CREATED").build();
      assertNotNull(audit.toString());
    }

    @Test
    void shouldHandleEqualsWithDifferentFields() {
      ClickhouseTenantCredentialAudit base = ClickhouseTenantCredentialAudit.builder()
          .id(1L).tenantId("t1").action("CREATED").performedBy("admin").build();
      
      assertNotEquals(base, ClickhouseTenantCredentialAudit.builder()
          .id(1L).tenantId("t2").action("CREATED").performedBy("admin").build());
      assertNotEquals(base, ClickhouseTenantCredentialAudit.builder()
          .id(1L).tenantId("t1").action("UPDATED").performedBy("admin").build());
      assertNotEquals(base, ClickhouseTenantCredentialAudit.builder()
          .id(1L).tenantId("t1").action("CREATED").performedBy("system").build());
    }

    @Test
    void shouldHandleEqualsWithNullVsNonNull() {
      ClickhouseTenantCredentialAudit a1 = ClickhouseTenantCredentialAudit.builder()
          .tenantId(null).build();
      ClickhouseTenantCredentialAudit a2 = ClickhouseTenantCredentialAudit.builder()
          .tenantId("t1").build();
      assertNotEquals(a1, a2);
      assertNotEquals(a2, a1);
    }

    @Test
    void shouldHandleHashCodeWithAllNulls() {
      ClickhouseTenantCredentialAudit a = new ClickhouseTenantCredentialAudit();
      assertNotNull(a.hashCode());
    }

    @Test
    void shouldHandleEqualsWithDetailsDiff() {
      ClickhouseTenantCredentialAudit a1 = ClickhouseTenantCredentialAudit.builder()
          .details("details1").build();
      ClickhouseTenantCredentialAudit a2 = ClickhouseTenantCredentialAudit.builder()
          .details("details2").build();
      assertNotEquals(a1, a2);
    }

    @Test
    void shouldHandleEqualsWithCreatedAtDiff() {
      ClickhouseTenantCredentialAudit a1 = ClickhouseTenantCredentialAudit.builder()
          .createdAt("2024-01-01").build();
      ClickhouseTenantCredentialAudit a2 = ClickhouseTenantCredentialAudit.builder()
          .createdAt("2024-01-02").build();
      assertNotEquals(a1, a2);
    }
  }

  @Nested
  class TestTenant {

    @Test
    void shouldBuildWithAllFields() {
      Tenant tenant = Tenant.builder()
          .tenantId("tenant_1")
          .name("Test Tenant")
          .description("Description")
          .isActive(true)
          .createdAt("2024-01-01")
          .updatedAt("2024-01-02")
          .gcpTenantId("gcp-123")
          .domainName("test.com")
          .build();

      assertEquals("tenant_1", tenant.getTenantId());
      assertEquals("Test Tenant", tenant.getName());
      assertEquals("Description", tenant.getDescription());
      assertTrue(tenant.getIsActive());
      assertEquals("2024-01-01", tenant.getCreatedAt());
      assertEquals("2024-01-02", tenant.getUpdatedAt());
      assertEquals("gcp-123", tenant.getGcpTenantId());
      assertEquals("test.com", tenant.getDomainName());
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      Tenant tenant = new Tenant();
      assertNull(tenant.getTenantId());
      assertNull(tenant.getIsActive());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      Tenant tenant = new Tenant(
          "t1", "name", "desc", true, "created", "updated", "gcp", "domain"
      );
      assertEquals("t1", tenant.getTenantId());
      assertEquals("name", tenant.getName());
      assertTrue(tenant.getIsActive());
    }

    @Test
    void shouldSetAndGetAllProperties() {
      Tenant tenant = new Tenant();
      tenant.setTenantId("t1");
      tenant.setName("name");
      tenant.setDescription("desc");
      tenant.setIsActive(false);
      tenant.setCreatedAt("created");
      tenant.setUpdatedAt("updated");
      tenant.setGcpTenantId("gcp");
      tenant.setDomainName("domain");

      assertEquals("t1", tenant.getTenantId());
      assertEquals("name", tenant.getName());
      assertEquals("desc", tenant.getDescription());
      assertFalse(tenant.getIsActive());
      assertEquals("created", tenant.getCreatedAt());
      assertEquals("updated", tenant.getUpdatedAt());
      assertEquals("gcp", tenant.getGcpTenantId());
      assertEquals("domain", tenant.getDomainName());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
      Tenant t1 = Tenant.builder().tenantId("t1").name("n1").build();
      Tenant t2 = Tenant.builder().tenantId("t1").name("n1").build();
      Tenant t3 = Tenant.builder().tenantId("t2").name("n1").build();

      assertEquals(t1, t2);
      assertNotEquals(t1, t3);
      assertEquals(t1, t1);
      assertNotEquals(t1, null);
      assertNotEquals(t1, "string");
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
      Tenant t1 = Tenant.builder().tenantId("t1").build();
      Tenant t2 = Tenant.builder().tenantId("t1").build();
      assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void shouldImplementToString() {
      Tenant tenant = Tenant.builder().tenantId("t1").name("Test").build();
      String str = tenant.toString();
      assertNotNull(str);
      assertTrue(str.contains("t1") || str.contains("tenantId"));
    }

    @Test
    void shouldHandleNullFieldsInEquals() {
      Tenant t1 = Tenant.builder().build();
      Tenant t2 = Tenant.builder().build();
      assertEquals(t1, t2);
    }

    @Test
    void shouldHandleEqualsWithDifferentFields() {
      Tenant base = Tenant.builder()
          .tenantId("t1").name("n1").isActive(true).gcpTenantId("gcp1").domainName("d1").build();
      
      assertNotEquals(base, Tenant.builder()
          .tenantId("t2").name("n1").isActive(true).gcpTenantId("gcp1").domainName("d1").build());
      assertNotEquals(base, Tenant.builder()
          .tenantId("t1").name("n2").isActive(true).gcpTenantId("gcp1").domainName("d1").build());
      assertNotEquals(base, Tenant.builder()
          .tenantId("t1").name("n1").isActive(false).gcpTenantId("gcp1").domainName("d1").build());
      assertNotEquals(base, Tenant.builder()
          .tenantId("t1").name("n1").isActive(true).gcpTenantId("gcp2").domainName("d1").build());
      assertNotEquals(base, Tenant.builder()
          .tenantId("t1").name("n1").isActive(true).gcpTenantId("gcp1").domainName("d2").build());
    }

    @Test
    void shouldHandleEqualsWithNullVsNonNull() {
      Tenant t1 = Tenant.builder().tenantId(null).build();
      Tenant t2 = Tenant.builder().tenantId("t1").build();
      assertNotEquals(t1, t2);
      assertNotEquals(t2, t1);
    }

    @Test
    void shouldHandleHashCodeWithAllNulls() {
      Tenant t = new Tenant();
      assertNotNull(t.hashCode());
    }

    @Test
    void shouldHandleEqualsWithDescriptionDiff() {
      Tenant t1 = Tenant.builder().description("d1").build();
      Tenant t2 = Tenant.builder().description("d2").build();
      assertNotEquals(t1, t2);
    }

    @Test
    void shouldHandleEqualsWithCreatedAtDiff() {
      Tenant t1 = Tenant.builder().createdAt("2024-01-01").build();
      Tenant t2 = Tenant.builder().createdAt("2024-01-02").build();
      assertNotEquals(t1, t2);
    }

    @Test
    void shouldHandleEqualsWithUpdatedAtDiff() {
      Tenant t1 = Tenant.builder().updatedAt("2024-01-01").build();
      Tenant t2 = Tenant.builder().updatedAt("2024-01-02").build();
      assertNotEquals(t1, t2);
    }

    @Test
    void shouldHandleEqualsWithAllFieldsSet() {
      Tenant t1 = Tenant.builder()
          .tenantId("t1").name("n1").description("d1").isActive(true)
          .createdAt("c1").updatedAt("u1").gcpTenantId("gcp1").domainName("dom1")
          .build();
      Tenant t2 = Tenant.builder()
          .tenantId("t1").name("n1").description("d1").isActive(true)
          .createdAt("c1").updatedAt("u1").gcpTenantId("gcp1").domainName("dom1")
          .build();
      assertEquals(t1, t2);
      assertEquals(t1.hashCode(), t2.hashCode());
    }
  }
}
