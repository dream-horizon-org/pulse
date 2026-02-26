package org.dreamhorizon.pulseserver.resources.tenants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models.ClickhouseTenantCredentialAudit;
import org.dreamhorizon.pulseserver.dao.tenantdao.models.Tenant;
import org.dreamhorizon.pulseserver.resources.tenants.models.AuditListRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.AuditLogRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.CreateCredentialsRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.CreateTenantRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.CredentialsRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantListRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.UpdateCredentialsRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.UpdateTenantRestRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateCredentialsRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.TenantInfo;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateCredentialsRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantMapperTest {

  private TenantMapper mapper;

  @BeforeEach
  void setup() {
    mapper = TenantMapper.INSTANCE;
  }

  @Nested
  class TestTenantMappings {

    @Test
    void shouldMapCreateTenantRestRequestToCreateTenantRequest() {
      CreateTenantRestRequest restRequest = CreateTenantRestRequest.builder()
          .tenantId("test_tenant")
          .name("Test Tenant")
          .description("Test Description")
          .gcpTenantId("gcp-123")
          .domainName("test.example.com")
          .build();

      CreateTenantRequest result = mapper.toCreateTenantRequest(restRequest);

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("Test Tenant", result.getName());
      assertEquals("Test Description", result.getDescription());
      assertEquals("gcp-123", result.getGcpTenantId());
      assertEquals("test.example.com", result.getDomainName());
      assertNull(result.getClickhousePassword()); // Should be ignored
    }

    @Test
    void shouldMapUpdateTenantRestRequestToUpdateTenantRequest() {
      UpdateTenantRestRequest restRequest = UpdateTenantRestRequest.builder()
          .name("Updated Name")
          .description("Updated Description")
          .build();

      UpdateTenantRequest result = mapper.toUpdateTenantRequest("test_tenant", restRequest);

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("Updated Name", result.getName());
      assertEquals("Updated Description", result.getDescription());
    }

    @Test
    void shouldMapTenantToTenantRestResponse() {
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

      TenantRestResponse result = mapper.toTenantRestResponse(tenant);

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("Test Tenant", result.getName());
      assertEquals("Test Description", result.getDescription());
      assertTrue(result.getIsActive());
      assertEquals("gcp-123", result.getGcpTenantId());
      assertEquals("test.example.com", result.getDomainName());
    }

    @Test
    void shouldMapTenantListToTenantListRestResponse() {
      Tenant tenant1 = Tenant.builder()
          .tenantId("tenant_1")
          .name("Tenant 1")
          .isActive(true)
          .build();

      Tenant tenant2 = Tenant.builder()
          .tenantId("tenant_2")
          .name("Tenant 2")
          .isActive(true)
          .build();

      List<Tenant> tenants = Arrays.asList(tenant1, tenant2);

      TenantListRestResponse result = mapper.toTenantListRestResponse(tenants);

      assertNotNull(result);
      assertEquals(2, result.getTotalCount());
      assertEquals(2, result.getTenants().size());
      assertEquals("tenant_1", result.getTenants().get(0).getTenantId());
      assertEquals("tenant_2", result.getTenants().get(1).getTenantId());
    }

    @Test
    void shouldHandleEmptyTenantList() {
      List<Tenant> emptyList = Collections.emptyList();

      TenantListRestResponse result = mapper.toTenantListRestResponse(emptyList);

      assertNotNull(result);
      assertEquals(0, result.getTotalCount());
      assertTrue(result.getTenants().isEmpty());
    }
  }

  @Nested
  class TestCredentialsMappings {

    @Test
    void shouldMapCreateCredentialsRestRequestToCreateCredentialsRequest() {
      CreateCredentialsRestRequest restRequest = CreateCredentialsRestRequest.builder()
          .clickhousePassword("password123")
          .build();

      CreateCredentialsRequest result = mapper.toCreateCredentialsRequest("test_tenant", restRequest);

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("password123", result.getClickhousePassword());
    }

    @Test
    void shouldMapUpdateCredentialsRestRequestToUpdateCredentialsRequest() {
      UpdateCredentialsRestRequest restRequest = UpdateCredentialsRestRequest.builder()
          .newPassword("newpassword456")
          .reason("Password rotation")
          .build();

      UpdateCredentialsRequest result = mapper.toUpdateCredentialsRequest("test_tenant", restRequest);

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("newpassword456", result.getNewPassword());
      assertEquals("Password rotation", result.getReason());
    }

    @Test
    void shouldMapTenantInfoToCredentialsRestResponse() {
      TenantInfo tenantInfo = TenantInfo.builder()
          .tenantId("test_tenant")
          .clickhouseUsername("tenant_test_tenant")
          .clickhousePassword("password123")
          .isActive(true)
          .message("Credentials created")
          .build();

      CredentialsRestResponse result = mapper.toCredentialsRestResponse(tenantInfo);

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("tenant_test_tenant", result.getClickhouseUsername());
      assertEquals("password123", result.getClickhousePassword());
      assertTrue(result.getIsActive());
      assertEquals("Credentials created", result.getMessage());
    }

    @Test
    void shouldMapClickhouseCredentialsToCredentialsRestResponse() {
      ClickhouseCredentials credentials = ClickhouseCredentials.builder()
          .tenantId("test_tenant")
          .clickhouseUsername("tenant_test_tenant")
          .clickhousePassword("should_be_ignored")
          .isActive(true)
          .createdAt("2026-01-01T00:00:00")
          .updatedAt("2026-01-01T00:00:00")
          .build();

      CredentialsRestResponse result = mapper.toCredentialsRestResponse(credentials);

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("tenant_test_tenant", result.getClickhouseUsername());
      assertNull(result.getClickhousePassword()); // Should be ignored
      assertNull(result.getMessage()); // Should be ignored
      assertTrue(result.getIsActive());
    }
  }

  @Nested
  class TestAuditMappings {

    @Test
    void shouldMapClickhouseTenantCredentialAuditToAuditLogRestResponse() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .id(1L)
          .tenantId("test_tenant")
          .action("CREDENTIALS_CREATED")
          .performedBy("admin@example.com")
          .details("{\"action\":\"test\"}")
          .createdAt("2026-01-01T00:00:00")
          .build();

      AuditLogRestResponse result = mapper.toAuditLogRestResponse(audit);

      assertNotNull(result);
      assertEquals(1L, result.getId());
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("CREDENTIALS_CREATED", result.getAction());
      assertEquals("admin@example.com", result.getPerformedBy());
      assertEquals("{\"action\":\"test\"}", result.getDetails());
      assertEquals("2026-01-01T00:00:00", result.getCreatedAt());
    }

    @Test
    void shouldMapAuditListToAuditListRestResponse() {
      ClickhouseTenantCredentialAudit audit1 = ClickhouseTenantCredentialAudit.builder()
          .id(1L)
          .tenantId("test_tenant")
          .action("CREDENTIALS_CREATED")
          .performedBy("admin@example.com")
          .createdAt("2026-01-01T00:00:00")
          .build();

      ClickhouseTenantCredentialAudit audit2 = ClickhouseTenantCredentialAudit.builder()
          .id(2L)
          .tenantId("test_tenant")
          .action("CREDENTIALS_UPDATED")
          .performedBy("admin@example.com")
          .createdAt("2026-01-01T01:00:00")
          .build();

      List<ClickhouseTenantCredentialAudit> audits = Arrays.asList(audit1, audit2);

      AuditListRestResponse result = mapper.toAuditListRestResponse(audits);

      assertNotNull(result);
      assertEquals(2, result.getTotalCount());
      assertEquals(2, result.getAuditLogs().size());
      assertEquals("CREDENTIALS_CREATED", result.getAuditLogs().get(0).getAction());
      assertEquals("CREDENTIALS_UPDATED", result.getAuditLogs().get(1).getAction());
    }

    @Test
    void shouldHandleEmptyAuditList() {
      List<ClickhouseTenantCredentialAudit> emptyList = Collections.emptyList();

      AuditListRestResponse result = mapper.toAuditListRestResponse(emptyList);

      assertNotNull(result);
      assertEquals(0, result.getTotalCount());
      assertTrue(result.getAuditLogs().isEmpty());
    }
  }

  @Nested
  class TestNullHandling {

    @Test
    void shouldHandleNullTenantFields() {
      Tenant tenant = Tenant.builder()
          .tenantId("test_tenant")
          .name(null)
          .description(null)
          .build();

      TenantRestResponse result = mapper.toTenantRestResponse(tenant);

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertNull(result.getName());
      assertNull(result.getDescription());
    }

    @Test
    void shouldHandleNullAuditFields() {
      ClickhouseTenantCredentialAudit audit = ClickhouseTenantCredentialAudit.builder()
          .id(1L)
          .tenantId("test_tenant")
          .action("CREDENTIALS_CREATED")
          .performedBy("admin@example.com")
          .details(null)
          .createdAt(null)
          .build();

      AuditLogRestResponse result = mapper.toAuditLogRestResponse(audit);

      assertNotNull(result);
      assertNull(result.getDetails());
      assertNull(result.getCreatedAt());
    }
  }
}
