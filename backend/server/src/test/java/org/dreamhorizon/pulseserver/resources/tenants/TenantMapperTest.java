package org.dreamhorizon.pulseserver.resources.tenants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.resources.tenants.models.CreateTenantRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantListRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantRestResponse;
import org.dreamhorizon.pulseserver.resources.tenants.models.UpdateTenantRestRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
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
  }
}
