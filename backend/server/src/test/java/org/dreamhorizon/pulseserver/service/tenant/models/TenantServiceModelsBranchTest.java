package org.dreamhorizon.pulseserver.service.tenant.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for tenant service models to cover all Lombok branches.
 */
class TenantServiceModelsBranchTest {

  @Nested
  class CreateCredentialsRequestTest {

    @Test
    void equalsShouldReturnTrueForSameObject() {
      CreateCredentialsRequest req = createCredentialsRequest();
      assertTrue(req.equals(req));
    }

    @Test
    void equalsShouldReturnFalseForNull() {
      CreateCredentialsRequest req = createCredentialsRequest();
      assertFalse(req.equals(null));
    }

    @Test
    void equalsShouldReturnFalseForDifferentClass() {
      CreateCredentialsRequest req = createCredentialsRequest();
      assertFalse(req.equals("string"));
    }

    @Test
    void equalsShouldReturnTrueForEqualObjects() {
      CreateCredentialsRequest req1 = createCredentialsRequest();
      CreateCredentialsRequest req2 = createCredentialsRequest();
      assertTrue(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenTenantIdDiffers() {
      CreateCredentialsRequest req1 = createCredentialsRequest();
      CreateCredentialsRequest req2 = CreateCredentialsRequest.builder()
          .tenantId("different")
          .clickhousePassword("pass1")
          .build();
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenPasswordDiffers() {
      CreateCredentialsRequest req1 = createCredentialsRequest();
      CreateCredentialsRequest req2 = CreateCredentialsRequest.builder()
          .tenantId("tenant1")
          .clickhousePassword("different")
          .build();
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldHandleNullFields() {
      CreateCredentialsRequest req1 = CreateCredentialsRequest.builder()
          .tenantId(null)
          .clickhousePassword(null)
          .build();
      CreateCredentialsRequest req2 = CreateCredentialsRequest.builder()
          .tenantId(null)
          .clickhousePassword(null)
          .build();
      assertTrue(req1.equals(req2));
    }

    @Test
    void hashCodeShouldBeConsistent() {
      CreateCredentialsRequest req = createCredentialsRequest();
      assertEquals(req.hashCode(), req.hashCode());
    }

    @Test
    void hashCodeShouldHandleNullFields() {
      CreateCredentialsRequest req = CreateCredentialsRequest.builder().build();
      assertNotNull(req.hashCode());
    }

    @Test
    void toStringShouldNotReturnNull() {
      CreateCredentialsRequest req = createCredentialsRequest();
      assertNotNull(req.toString());
    }

    @Test
    void canEqualShouldWork() {
      CreateCredentialsRequest req = createCredentialsRequest();
      assertTrue(req.canEqual(createCredentialsRequest()));
      assertFalse(req.canEqual("string"));
    }

    @Test
    void builderToStringShouldWork() {
      String str = CreateCredentialsRequest.builder().tenantId("test").toString();
      assertNotNull(str);
    }

    private CreateCredentialsRequest createCredentialsRequest() {
      return CreateCredentialsRequest.builder()
          .tenantId("tenant1")
          .clickhousePassword("pass1")
          .build();
    }
  }

  @Nested
  class CreateTenantRequestTest {

    @Test
    void equalsShouldReturnTrueForSameObject() {
      CreateTenantRequest req = createTenantRequest();
      assertTrue(req.equals(req));
    }

    @Test
    void equalsShouldReturnFalseForNull() {
      CreateTenantRequest req = createTenantRequest();
      assertFalse(req.equals(null));
    }

    @Test
    void equalsShouldReturnFalseForDifferentClass() {
      CreateTenantRequest req = createTenantRequest();
      assertFalse(req.equals("string"));
    }

    @Test
    void equalsShouldReturnTrueForEqualObjects() {
      CreateTenantRequest req1 = createTenantRequest();
      CreateTenantRequest req2 = createTenantRequest();
      assertTrue(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenTenantIdDiffers() {
      CreateTenantRequest req1 = createTenantRequest();
      CreateTenantRequest req2 = createTenantRequest();
      req2.setTenantId("different");
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenNameDiffers() {
      CreateTenantRequest req1 = createTenantRequest();
      CreateTenantRequest req2 = createTenantRequest();
      req2.setName("different");
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenDescriptionDiffers() {
      CreateTenantRequest req1 = createTenantRequest();
      CreateTenantRequest req2 = createTenantRequest();
      req2.setDescription("different");
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenGcpTenantIdDiffers() {
      CreateTenantRequest req1 = createTenantRequest();
      CreateTenantRequest req2 = createTenantRequest();
      req2.setGcpTenantId("different");
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenDomainNameDiffers() {
      CreateTenantRequest req1 = createTenantRequest();
      CreateTenantRequest req2 = createTenantRequest();
      req2.setDomainName("different");
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldHandleNullFields() {
      CreateTenantRequest req1 = CreateTenantRequest.builder().build();
      CreateTenantRequest req2 = CreateTenantRequest.builder().build();
      assertTrue(req1.equals(req2));
    }

    @Test
    void hashCodeShouldBeConsistent() {
      CreateTenantRequest req = createTenantRequest();
      assertEquals(req.hashCode(), req.hashCode());
    }

    @Test
    void toStringShouldNotReturnNull() {
      CreateTenantRequest req = createTenantRequest();
      assertNotNull(req.toString());
    }

    @Test
    void canEqualShouldWork() {
      CreateTenantRequest req = createTenantRequest();
      assertTrue(req.canEqual(createTenantRequest()));
      assertFalse(req.canEqual("string"));
    }

    private CreateTenantRequest createTenantRequest() {
      return CreateTenantRequest.builder()
          .tenantId("tenant1")
          .name("Test Tenant")
          .description("Description")
          .gcpTenantId("gcp1")
          .domainName("example.com")
          .build();
    }
  }

  @Nested
  class UpdateCredentialsRequestTest {

    @Test
    void equalsShouldReturnTrueForSameObject() {
      UpdateCredentialsRequest req = createUpdateCredentialsRequest();
      assertTrue(req.equals(req));
    }

    @Test
    void equalsShouldReturnFalseForNull() {
      UpdateCredentialsRequest req = createUpdateCredentialsRequest();
      assertFalse(req.equals(null));
    }

    @Test
    void equalsShouldReturnFalseForDifferentClass() {
      UpdateCredentialsRequest req = createUpdateCredentialsRequest();
      assertFalse(req.equals("string"));
    }

    @Test
    void equalsShouldReturnTrueForEqualObjects() {
      UpdateCredentialsRequest req1 = createUpdateCredentialsRequest();
      UpdateCredentialsRequest req2 = createUpdateCredentialsRequest();
      assertTrue(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenTenantIdDiffers() {
      UpdateCredentialsRequest req1 = createUpdateCredentialsRequest();
      UpdateCredentialsRequest req2 = createUpdateCredentialsRequest();
      req2.setTenantId("different");
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenNewPasswordDiffers() {
      UpdateCredentialsRequest req1 = createUpdateCredentialsRequest();
      UpdateCredentialsRequest req2 = createUpdateCredentialsRequest();
      req2.setNewPassword("different");
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenReasonDiffers() {
      UpdateCredentialsRequest req1 = createUpdateCredentialsRequest();
      UpdateCredentialsRequest req2 = createUpdateCredentialsRequest();
      req2.setReason("different");
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldHandleNullFields() {
      UpdateCredentialsRequest req1 = UpdateCredentialsRequest.builder().build();
      UpdateCredentialsRequest req2 = UpdateCredentialsRequest.builder().build();
      assertTrue(req1.equals(req2));
    }

    @Test
    void hashCodeShouldBeConsistent() {
      UpdateCredentialsRequest req = createUpdateCredentialsRequest();
      assertEquals(req.hashCode(), req.hashCode());
    }

    @Test
    void toStringShouldNotReturnNull() {
      UpdateCredentialsRequest req = createUpdateCredentialsRequest();
      assertNotNull(req.toString());
    }

    @Test
    void canEqualShouldWork() {
      UpdateCredentialsRequest req = createUpdateCredentialsRequest();
      assertTrue(req.canEqual(createUpdateCredentialsRequest()));
      assertFalse(req.canEqual("string"));
    }

    private UpdateCredentialsRequest createUpdateCredentialsRequest() {
      return UpdateCredentialsRequest.builder()
          .tenantId("tenant1")
          .newPassword("newpass")
          .reason("test reason")
          .build();
    }
  }

  @Nested
  class UpdateTenantRequestTest {

    @Test
    void equalsShouldReturnTrueForSameObject() {
      UpdateTenantRequest req = createUpdateTenantRequest();
      assertTrue(req.equals(req));
    }

    @Test
    void equalsShouldReturnFalseForNull() {
      UpdateTenantRequest req = createUpdateTenantRequest();
      assertFalse(req.equals(null));
    }

    @Test
    void equalsShouldReturnFalseForDifferentClass() {
      UpdateTenantRequest req = createUpdateTenantRequest();
      assertFalse(req.equals("string"));
    }

    @Test
    void equalsShouldReturnTrueForEqualObjects() {
      UpdateTenantRequest req1 = createUpdateTenantRequest();
      UpdateTenantRequest req2 = createUpdateTenantRequest();
      assertTrue(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenTenantIdDiffers() {
      UpdateTenantRequest req1 = createUpdateTenantRequest();
      UpdateTenantRequest req2 = createUpdateTenantRequest();
      req2.setTenantId("different");
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenNameDiffers() {
      UpdateTenantRequest req1 = createUpdateTenantRequest();
      UpdateTenantRequest req2 = createUpdateTenantRequest();
      req2.setName("different");
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldReturnFalseWhenDescriptionDiffers() {
      UpdateTenantRequest req1 = createUpdateTenantRequest();
      UpdateTenantRequest req2 = createUpdateTenantRequest();
      req2.setDescription("different");
      assertFalse(req1.equals(req2));
    }

    @Test
    void equalsShouldHandleNullFields() {
      UpdateTenantRequest req1 = UpdateTenantRequest.builder().build();
      UpdateTenantRequest req2 = UpdateTenantRequest.builder().build();
      assertTrue(req1.equals(req2));
    }

    @Test
    void hashCodeShouldBeConsistent() {
      UpdateTenantRequest req = createUpdateTenantRequest();
      assertEquals(req.hashCode(), req.hashCode());
    }

    @Test
    void toStringShouldNotReturnNull() {
      UpdateTenantRequest req = createUpdateTenantRequest();
      assertNotNull(req.toString());
    }

    @Test
    void canEqualShouldWork() {
      UpdateTenantRequest req = createUpdateTenantRequest();
      assertTrue(req.canEqual(createUpdateTenantRequest()));
      assertFalse(req.canEqual("string"));
    }

    private UpdateTenantRequest createUpdateTenantRequest() {
      return UpdateTenantRequest.builder()
          .tenantId("tenant1")
          .name("Updated Name")
          .description("Updated Description")
          .build();
    }
  }

  @Nested
  class TenantInfoTest {

    @Test
    void equalsShouldReturnTrueForSameObject() {
      TenantInfo info = createTenantInfo();
      assertTrue(info.equals(info));
    }

    @Test
    void equalsShouldReturnFalseForNull() {
      TenantInfo info = createTenantInfo();
      assertFalse(info.equals(null));
    }

    @Test
    void equalsShouldReturnFalseForDifferentClass() {
      TenantInfo info = createTenantInfo();
      assertFalse(info.equals("string"));
    }

    @Test
    void equalsShouldReturnTrueForEqualObjects() {
      TenantInfo info1 = createTenantInfo();
      TenantInfo info2 = createTenantInfo();
      assertTrue(info1.equals(info2));
    }

    @Test
    void equalsShouldReturnFalseWhenTenantIdDiffers() {
      TenantInfo info1 = createTenantInfo();
      TenantInfo info2 = createTenantInfo();
      info2.setTenantId("different");
      assertFalse(info1.equals(info2));
    }

    @Test
    void equalsShouldReturnFalseWhenClickhouseUsernameDiffers() {
      TenantInfo info1 = createTenantInfo();
      TenantInfo info2 = createTenantInfo();
      info2.setClickhouseUsername("different");
      assertFalse(info1.equals(info2));
    }

    @Test
    void equalsShouldReturnFalseWhenIsActiveDiffers() {
      TenantInfo info1 = createTenantInfo();
      TenantInfo info2 = createTenantInfo();
      info2.setIsActive(false);
      assertFalse(info1.equals(info2));
    }

    @Test
    void equalsShouldReturnFalseWhenCreatedAtDiffers() {
      TenantInfo info1 = createTenantInfo();
      TenantInfo info2 = createTenantInfo();
      info2.setCreatedAt("different");
      assertFalse(info1.equals(info2));
    }

    @Test
    void equalsShouldReturnFalseWhenUpdatedAtDiffers() {
      TenantInfo info1 = createTenantInfo();
      TenantInfo info2 = createTenantInfo();
      info2.setUpdatedAt("different");
      assertFalse(info1.equals(info2));
    }

    @Test
    void equalsShouldHandleNullFields() {
      TenantInfo info1 = TenantInfo.builder().build();
      TenantInfo info2 = TenantInfo.builder().build();
      assertTrue(info1.equals(info2));
    }

    @Test
    void hashCodeShouldBeConsistent() {
      TenantInfo info = createTenantInfo();
      assertEquals(info.hashCode(), info.hashCode());
    }

    @Test
    void toStringShouldNotReturnNull() {
      TenantInfo info = createTenantInfo();
      assertNotNull(info.toString());
    }

    @Test
    void canEqualShouldWork() {
      TenantInfo info = createTenantInfo();
      assertTrue(info.canEqual(createTenantInfo()));
      assertFalse(info.canEqual("string"));
    }

    private TenantInfo createTenantInfo() {
      return TenantInfo.builder()
          .tenantId("tenant1")
          .clickhouseUsername("user1")
          .isActive(true)
          .createdAt("2024-01-01")
          .updatedAt("2024-01-02")
          .build();
    }
  }
}
