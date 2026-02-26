package org.dreamhorizon.pulseserver.service.tenant.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantModelsTest {

  @Nested
  class TestCreateTenantRequest {

    @Test
    void shouldBuildWithAllFields() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("tenant_1")
          .name("Test Tenant")
          .description("Description")
          .clickhousePassword("password")
          .gcpTenantId("gcp-123")
          .domainName("test.com")
          .build();

      assertEquals("tenant_1", request.getTenantId());
      assertEquals("Test Tenant", request.getName());
      assertEquals("Description", request.getDescription());
      assertEquals("password", request.getClickhousePassword());
      assertEquals("gcp-123", request.getGcpTenantId());
      assertEquals("test.com", request.getDomainName());
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      CreateTenantRequest request = new CreateTenantRequest();
      assertNull(request.getTenantId());
      assertNull(request.getName());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      CreateTenantRequest request = new CreateTenantRequest(
          "tenant_1", "name", "desc", "pass", "gcp", "domain"
      );
      assertEquals("tenant_1", request.getTenantId());
      assertEquals("name", request.getName());
    }

    @Test
    void shouldSetAndGetProperties() {
      CreateTenantRequest request = new CreateTenantRequest();
      request.setTenantId("tenant_1");
      request.setName("Test");
      request.setDescription("Desc");
      request.setClickhousePassword("pass");
      request.setGcpTenantId("gcp");
      request.setDomainName("domain");

      assertEquals("tenant_1", request.getTenantId());
      assertEquals("Test", request.getName());
      assertEquals("Desc", request.getDescription());
      assertEquals("pass", request.getClickhousePassword());
      assertEquals("gcp", request.getGcpTenantId());
      assertEquals("domain", request.getDomainName());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
      CreateTenantRequest r1 = CreateTenantRequest.builder().tenantId("t1").name("n1").build();
      CreateTenantRequest r2 = CreateTenantRequest.builder().tenantId("t1").name("n1").build();
      CreateTenantRequest r3 = CreateTenantRequest.builder().tenantId("t2").name("n1").build();

      assertEquals(r1, r2);
      assertNotEquals(r1, r3);
      assertEquals(r1, r1);
      assertNotEquals(r1, null);
      assertNotEquals(r1, "string");
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
      CreateTenantRequest r1 = CreateTenantRequest.builder().tenantId("t1").name("n1").build();
      CreateTenantRequest r2 = CreateTenantRequest.builder().tenantId("t1").name("n1").build();

      assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void shouldImplementToString() {
      CreateTenantRequest request = CreateTenantRequest.builder().tenantId("t1").build();
      String str = request.toString();
      assertNotNull(str);
      assertTrue(str.contains("t1") || str.contains("tenantId"));
    }

    @Test
    void shouldHandleNullFields() {
      CreateTenantRequest r1 = CreateTenantRequest.builder().build();
      CreateTenantRequest r2 = CreateTenantRequest.builder().build();
      assertEquals(r1, r2);
      assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void shouldHandleEqualsWithDifferentFields() {
      CreateTenantRequest base = CreateTenantRequest.builder()
          .tenantId("t1").name("n1").description("d1").build();
      
      assertNotEquals(base, CreateTenantRequest.builder()
          .tenantId("t2").name("n1").description("d1").build());
      assertNotEquals(base, CreateTenantRequest.builder()
          .tenantId("t1").name("n2").description("d1").build());
      assertNotEquals(base, CreateTenantRequest.builder()
          .tenantId("t1").name("n1").description("d2").build());
    }

    @Test
    void shouldHandleEqualsWithNullAndNonNullFields() {
      CreateTenantRequest withNull = CreateTenantRequest.builder().tenantId(null).build();
      CreateTenantRequest withValue = CreateTenantRequest.builder().tenantId("t1").build();
      assertNotEquals(withNull, withValue);
      assertNotEquals(withValue, withNull);
    }

    @Test
    void shouldHandleEqualsWithAllFieldsNull() {
      CreateTenantRequest r1 = new CreateTenantRequest();
      CreateTenantRequest r2 = new CreateTenantRequest();
      assertEquals(r1, r2);
    }

    @Test
    void shouldHandleHashCodeWithNullFields() {
      CreateTenantRequest r = new CreateTenantRequest();
      assertNotNull(r.hashCode());
    }

    @Test
    void shouldHandleEqualsWithPartialNulls() {
      CreateTenantRequest r1 = CreateTenantRequest.builder()
          .tenantId("t1").name(null).description("d1").clickhousePassword(null)
          .gcpTenantId("gcp1").domainName(null).build();
      CreateTenantRequest r2 = CreateTenantRequest.builder()
          .tenantId("t1").name("n1").description("d1").clickhousePassword(null)
          .gcpTenantId("gcp1").domainName(null).build();
      assertNotEquals(r1, r2);
    }

    @Test
    void shouldHandleEqualsSymmetry() {
      CreateTenantRequest r1 = CreateTenantRequest.builder().tenantId("t1").build();
      CreateTenantRequest r2 = CreateTenantRequest.builder().tenantId("t1").build();
      assertEquals(r1.equals(r2), r2.equals(r1));
    }
  }

  @Nested
  class TestTenantInfo {

    @Test
    void shouldBuildWithAllFields() {
      TenantInfo info = TenantInfo.builder()
          .tenantId("tenant_1")
          .name("Test Tenant")
          .description("Description")
          .clickhouseUsername("ch_user")
          .clickhousePassword("ch_pass")
          .isActive(true)
          .createdAt("2024-01-01")
          .updatedAt("2024-01-02")
          .message("Success")
          .build();

      assertEquals("tenant_1", info.getTenantId());
      assertEquals("Test Tenant", info.getName());
      assertEquals("Description", info.getDescription());
      assertEquals("ch_user", info.getClickhouseUsername());
      assertEquals("ch_pass", info.getClickhousePassword());
      assertTrue(info.getIsActive());
      assertEquals("2024-01-01", info.getCreatedAt());
      assertEquals("2024-01-02", info.getUpdatedAt());
      assertEquals("Success", info.getMessage());
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      TenantInfo info = new TenantInfo();
      assertNull(info.getTenantId());
      assertNull(info.getIsActive());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      TenantInfo info = new TenantInfo(
          "t1", "name", "desc", "user", "pass", true, "created", "updated", "msg"
      );
      assertEquals("t1", info.getTenantId());
      assertTrue(info.getIsActive());
    }

    @Test
    void shouldSetAndGetAllProperties() {
      TenantInfo info = new TenantInfo();
      info.setTenantId("tenant_1");
      info.setName("Name");
      info.setDescription("Desc");
      info.setClickhouseUsername("user");
      info.setClickhousePassword("pass");
      info.setIsActive(false);
      info.setCreatedAt("created");
      info.setUpdatedAt("updated");
      info.setMessage("msg");

      assertEquals("tenant_1", info.getTenantId());
      assertEquals("Name", info.getName());
      assertEquals("Desc", info.getDescription());
      assertEquals("user", info.getClickhouseUsername());
      assertEquals("pass", info.getClickhousePassword());
      assertFalse(info.getIsActive());
      assertEquals("created", info.getCreatedAt());
      assertEquals("updated", info.getUpdatedAt());
      assertEquals("msg", info.getMessage());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
      TenantInfo i1 = TenantInfo.builder().tenantId("t1").isActive(true).build();
      TenantInfo i2 = TenantInfo.builder().tenantId("t1").isActive(true).build();
      TenantInfo i3 = TenantInfo.builder().tenantId("t2").isActive(true).build();

      assertEquals(i1, i2);
      assertNotEquals(i1, i3);
      assertEquals(i1, i1);
      assertNotEquals(i1, null);
      assertNotEquals(i1, "string");
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
      TenantInfo i1 = TenantInfo.builder().tenantId("t1").build();
      TenantInfo i2 = TenantInfo.builder().tenantId("t1").build();
      assertEquals(i1.hashCode(), i2.hashCode());
    }

    @Test
    void shouldImplementToString() {
      TenantInfo info = TenantInfo.builder().tenantId("t1").message("msg").build();
      String str = info.toString();
      assertNotNull(str);
      assertTrue(str.contains("t1") || str.contains("tenantId"));
    }

    @Test
    void shouldHandleNullFields() {
      TenantInfo i1 = TenantInfo.builder().build();
      TenantInfo i2 = TenantInfo.builder().build();
      assertEquals(i1, i2);
    }

    @Test
    void shouldHandleEqualsWithDifferentFields() {
      TenantInfo base = TenantInfo.builder()
          .tenantId("t1").name("n1").isActive(true).message("m1").build();
      
      assertNotEquals(base, TenantInfo.builder()
          .tenantId("t2").name("n1").isActive(true).message("m1").build());
      assertNotEquals(base, TenantInfo.builder()
          .tenantId("t1").name("n2").isActive(true).message("m1").build());
      assertNotEquals(base, TenantInfo.builder()
          .tenantId("t1").name("n1").isActive(false).message("m1").build());
      assertNotEquals(base, TenantInfo.builder()
          .tenantId("t1").name("n1").isActive(true).message("m2").build());
    }

    @Test
    void shouldHandleEqualsWithNullAndNonNull() {
      TenantInfo withNull = TenantInfo.builder().tenantId(null).isActive(null).build();
      TenantInfo withValue = TenantInfo.builder().tenantId("t1").isActive(true).build();
      assertNotEquals(withNull, withValue);
      assertNotEquals(withValue, withNull);
    }

    @Test
    void shouldHandleEqualsWithAllFieldsDifferent() {
      TenantInfo i1 = TenantInfo.builder()
          .tenantId("t1").name("n1").description("d1").clickhouseUsername("u1")
          .clickhousePassword("p1").isActive(true).createdAt("c1").updatedAt("u1").message("m1")
          .build();
      TenantInfo i2 = TenantInfo.builder()
          .tenantId("t2").name("n2").description("d2").clickhouseUsername("u2")
          .clickhousePassword("p2").isActive(false).createdAt("c2").updatedAt("u2").message("m2")
          .build();
      assertNotEquals(i1, i2);
    }

    @Test
    void shouldHandleHashCodeWithNullFields() {
      TenantInfo info = new TenantInfo();
      assertNotNull(info.hashCode());
    }

    @Test
    void shouldHandleEqualsWithOneFieldNull() {
      TenantInfo i1 = TenantInfo.builder().tenantId("t1").clickhouseUsername(null).build();
      TenantInfo i2 = TenantInfo.builder().tenantId("t1").clickhouseUsername("user").build();
      assertNotEquals(i1, i2);
    }

    @Test
    void shouldHandleEqualsWithDescriptionDifference() {
      TenantInfo i1 = TenantInfo.builder().description("d1").build();
      TenantInfo i2 = TenantInfo.builder().description("d2").build();
      assertNotEquals(i1, i2);
    }

    @Test
    void shouldHandleEqualsWithPasswordDifference() {
      TenantInfo i1 = TenantInfo.builder().clickhousePassword("p1").build();
      TenantInfo i2 = TenantInfo.builder().clickhousePassword("p2").build();
      assertNotEquals(i1, i2);
    }

    @Test
    void shouldHandleEqualsWithCreatedAtDifference() {
      TenantInfo i1 = TenantInfo.builder().createdAt("2024-01-01").build();
      TenantInfo i2 = TenantInfo.builder().createdAt("2024-01-02").build();
      assertNotEquals(i1, i2);
    }

    @Test
    void shouldHandleEqualsWithUpdatedAtDifference() {
      TenantInfo i1 = TenantInfo.builder().updatedAt("2024-01-01").build();
      TenantInfo i2 = TenantInfo.builder().updatedAt("2024-01-02").build();
      assertNotEquals(i1, i2);
    }
  }

  @Nested
  class TestUpdateCredentialsRequest {

    @Test
    void shouldBuildWithAllFields() {
      UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
          .tenantId("tenant_1")
          .newPassword("newPass")
          .reason("rotation")
          .build();

      assertEquals("tenant_1", request.getTenantId());
      assertEquals("newPass", request.getNewPassword());
      assertEquals("rotation", request.getReason());
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      UpdateCredentialsRequest request = new UpdateCredentialsRequest();
      assertNull(request.getTenantId());
    }

    @Test
    void shouldSetAndGetProperties() {
      UpdateCredentialsRequest request = new UpdateCredentialsRequest();
      request.setTenantId("t1");
      request.setNewPassword("pass");
      request.setReason("reason");

      assertEquals("t1", request.getTenantId());
      assertEquals("pass", request.getNewPassword());
      assertEquals("reason", request.getReason());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
      UpdateCredentialsRequest r1 = UpdateCredentialsRequest.builder().tenantId("t1").build();
      UpdateCredentialsRequest r2 = UpdateCredentialsRequest.builder().tenantId("t1").build();
      UpdateCredentialsRequest r3 = UpdateCredentialsRequest.builder().tenantId("t2").build();

      assertEquals(r1, r2);
      assertNotEquals(r1, r3);
      assertEquals(r1, r1);
      assertNotEquals(r1, null);
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
      UpdateCredentialsRequest r1 = UpdateCredentialsRequest.builder().tenantId("t1").build();
      UpdateCredentialsRequest r2 = UpdateCredentialsRequest.builder().tenantId("t1").build();
      assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void shouldImplementToString() {
      UpdateCredentialsRequest request = UpdateCredentialsRequest.builder().tenantId("t1").build();
      assertNotNull(request.toString());
    }
  }

  @Nested
  class TestUpdateTenantRequest {

    @Test
    void shouldBuildWithAllFields() {
      UpdateTenantRequest request = UpdateTenantRequest.builder()
          .tenantId("tenant_1")
          .name("New Name")
          .description("New Desc")
          .build();

      assertEquals("tenant_1", request.getTenantId());
      assertEquals("New Name", request.getName());
      assertEquals("New Desc", request.getDescription());
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      UpdateTenantRequest request = new UpdateTenantRequest();
      assertNull(request.getTenantId());
    }

    @Test
    void shouldSetAndGetProperties() {
      UpdateTenantRequest request = new UpdateTenantRequest();
      request.setTenantId("t1");
      request.setName("name");
      request.setDescription("desc");

      assertEquals("t1", request.getTenantId());
      assertEquals("name", request.getName());
      assertEquals("desc", request.getDescription());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
      UpdateTenantRequest r1 = UpdateTenantRequest.builder().tenantId("t1").build();
      UpdateTenantRequest r2 = UpdateTenantRequest.builder().tenantId("t1").build();
      UpdateTenantRequest r3 = UpdateTenantRequest.builder().tenantId("t2").build();

      assertEquals(r1, r2);
      assertNotEquals(r1, r3);
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
      UpdateTenantRequest r1 = UpdateTenantRequest.builder().tenantId("t1").build();
      UpdateTenantRequest r2 = UpdateTenantRequest.builder().tenantId("t1").build();
      assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void shouldImplementToString() {
      UpdateTenantRequest request = UpdateTenantRequest.builder().tenantId("t1").build();
      assertNotNull(request.toString());
    }
  }

  @Nested
  class TestCreateCredentialsRequest {

    @Test
    void shouldBuildWithAllFields() {
      CreateCredentialsRequest request = CreateCredentialsRequest.builder()
          .tenantId("tenant_1")
          .clickhousePassword("password")
          .build();

      assertEquals("tenant_1", request.getTenantId());
      assertEquals("password", request.getClickhousePassword());
    }

    @Test
    void shouldCreateWithNoArgsConstructor() {
      CreateCredentialsRequest request = new CreateCredentialsRequest();
      assertNull(request.getTenantId());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      CreateCredentialsRequest request = new CreateCredentialsRequest("t1", "pass");
      assertEquals("t1", request.getTenantId());
      assertEquals("pass", request.getClickhousePassword());
    }

    @Test
    void shouldSetAndGetProperties() {
      CreateCredentialsRequest request = new CreateCredentialsRequest();
      request.setTenantId("t1");
      request.setClickhousePassword("pass");

      assertEquals("t1", request.getTenantId());
      assertEquals("pass", request.getClickhousePassword());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
      CreateCredentialsRequest r1 = CreateCredentialsRequest.builder().tenantId("t1").build();
      CreateCredentialsRequest r2 = CreateCredentialsRequest.builder().tenantId("t1").build();
      CreateCredentialsRequest r3 = CreateCredentialsRequest.builder().tenantId("t2").build();

      assertEquals(r1, r2);
      assertEquals(r1, r1);
      assertNotEquals(r1, r3);
      assertNotEquals(r1, null);
      assertNotEquals(r1, "string");
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
      CreateCredentialsRequest r1 = CreateCredentialsRequest.builder().tenantId("t1").build();
      CreateCredentialsRequest r2 = CreateCredentialsRequest.builder().tenantId("t1").build();
      assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void shouldImplementToString() {
      CreateCredentialsRequest request = CreateCredentialsRequest.builder().tenantId("t1").build();
      String str = request.toString();
      assertNotNull(str);
      assertTrue(str.contains("t1") || str.contains("tenantId"));
    }

    @Test
    void shouldHandleEqualsWithDifferentFields() {
      CreateCredentialsRequest r1 = CreateCredentialsRequest.builder()
          .tenantId("t1").clickhousePassword("p1").build();
      CreateCredentialsRequest r2 = CreateCredentialsRequest.builder()
          .tenantId("t1").clickhousePassword("p2").build();
      assertNotEquals(r1, r2);
    }

    @Test
    void shouldHandleNullVsNonNull() {
      CreateCredentialsRequest r1 = CreateCredentialsRequest.builder().tenantId(null).build();
      CreateCredentialsRequest r2 = CreateCredentialsRequest.builder().tenantId("t1").build();
      assertNotEquals(r1, r2);
      assertNotEquals(r2, r1);
    }

    @Test
    void shouldHandleAllNullFields() {
      CreateCredentialsRequest r1 = new CreateCredentialsRequest();
      CreateCredentialsRequest r2 = new CreateCredentialsRequest();
      assertEquals(r1, r2);
      assertEquals(r1.hashCode(), r2.hashCode());
    }
  }

  @Nested
  class TestUpdateCredentialsRequestAllArgs {

    @Test
    void shouldCreateWithAllArgsConstructor() {
      UpdateCredentialsRequest request = new UpdateCredentialsRequest("t1", "pass", "reason");
      assertEquals("t1", request.getTenantId());
      assertEquals("pass", request.getNewPassword());
      assertEquals("reason", request.getReason());
    }

    @Test
    void shouldHandleEqualsWithDifferentFields() {
      UpdateCredentialsRequest r1 = UpdateCredentialsRequest.builder()
          .tenantId("t1").newPassword("p1").reason("r1").build();
      
      assertNotEquals(r1, UpdateCredentialsRequest.builder()
          .tenantId("t1").newPassword("p2").reason("r1").build());
      assertNotEquals(r1, UpdateCredentialsRequest.builder()
          .tenantId("t1").newPassword("p1").reason("r2").build());
    }

    @Test
    void shouldHandleNullVsNonNull() {
      UpdateCredentialsRequest r1 = UpdateCredentialsRequest.builder().tenantId(null).build();
      UpdateCredentialsRequest r2 = UpdateCredentialsRequest.builder().tenantId("t1").build();
      assertNotEquals(r1, r2);
      assertNotEquals(r2, r1);
    }

    @Test
    void shouldHandleAllNullFields() {
      UpdateCredentialsRequest r1 = new UpdateCredentialsRequest();
      UpdateCredentialsRequest r2 = new UpdateCredentialsRequest();
      assertEquals(r1, r2);
      assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void shouldImplementToStringWithValues() {
      UpdateCredentialsRequest r = UpdateCredentialsRequest.builder()
          .tenantId("t1").newPassword("p1").reason("r1").build();
      String str = r.toString();
      assertNotNull(str);
    }
  }

  @Nested
  class TestUpdateTenantRequestAllArgs {

    @Test
    void shouldCreateWithAllArgsConstructor() {
      UpdateTenantRequest request = new UpdateTenantRequest("t1", "name", "desc");
      assertEquals("t1", request.getTenantId());
      assertEquals("name", request.getName());
      assertEquals("desc", request.getDescription());
    }

    @Test
    void shouldHandleEqualsWithDifferentFields() {
      UpdateTenantRequest r1 = UpdateTenantRequest.builder()
          .tenantId("t1").name("n1").description("d1").build();
      
      assertNotEquals(r1, UpdateTenantRequest.builder()
          .tenantId("t1").name("n2").description("d1").build());
      assertNotEquals(r1, UpdateTenantRequest.builder()
          .tenantId("t1").name("n1").description("d2").build());
    }

    @Test
    void shouldHandleNullVsNonNull() {
      UpdateTenantRequest r1 = UpdateTenantRequest.builder().tenantId(null).build();
      UpdateTenantRequest r2 = UpdateTenantRequest.builder().tenantId("t1").build();
      assertNotEquals(r1, r2);
      assertNotEquals(r2, r1);
    }

    @Test
    void shouldHandleAllNullFields() {
      UpdateTenantRequest r1 = new UpdateTenantRequest();
      UpdateTenantRequest r2 = new UpdateTenantRequest();
      assertEquals(r1, r2);
      assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void shouldImplementToStringWithValues() {
      UpdateTenantRequest r = UpdateTenantRequest.builder()
          .tenantId("t1").name("n1").description("d1").build();
      String str = r.toString();
      assertNotNull(str);
    }
  }
}
