package org.dreamhorizon.pulseserver.service.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseTenantConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.ClickhouseCredentialsDao;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseTenantCredentialAudit;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateCredentialsRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.TenantInfo;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateCredentialsRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TenantServiceTest {

  @Mock
  TenantDao tenantDao;

  @Mock
  ClickhouseCredentialsDao credentialsDao;

  @Mock
  ClickhouseTenantConnectionPoolManager poolManager;

  @Mock
  org.dreamhorizon.pulseserver.service.OpenFgaService openFgaService;

  TenantService tenantService;

  @BeforeEach
  void setup() {
    tenantService = new TenantService(tenantDao, credentialsDao, poolManager, openFgaService);
    tenantService = new TenantService(tenantDao, credentialsDao, poolManager, openFgaService);
  }

  private Tenant createMockTenant() {
    return Tenant.builder()
        .tenantId("test_tenant")
        .name("Test Tenant")
        .description("Test Description")
        .gcpTenantId("gcp-test-123")
        .domainName("test.example.com")
        .isActive(true)
        .createdAt("2026-01-01T00:00:00")
        .updatedAt("2026-01-01T00:00:00")
        .build();
  }

  private ClickhouseCredentials createMockCredentials() {
    return ClickhouseCredentials.builder()
        .id(1L)
        .tenantId("test_tenant")
        .clickhouseUsername("tenant_test_tenant")
        .clickhousePassword("decrypted_password")
        .isActive(true)
        .createdAt("2026-01-01T00:00:00")
        .updatedAt("2026-01-01T00:00:00")
        .build();
  }

  private ClickhouseTenantCredentialAudit createMockAudit() {
    return ClickhouseTenantCredentialAudit.builder()
        .id(1L)
        .projectId("test_project")
        .action("CREDENTIALS_CREATED")
        .performedBy("admin@example.com")
        .details("{\"action\":\"test\"}")
        .createdAt("2026-01-01T00:00:00")
        .build();
  }

  // ==================== TENANT CRUD TESTS ====================

  @Nested
  class TestCreateTenant {

    @Test
    void shouldCreateTenantSuccessfully() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("test_tenant")
          .name("Test Tenant")
          .description("Test Description")
          .gcpTenantId("gcp-test-123")
          .domainName("test.example.com")
          .build();

      Tenant mockTenant = createMockTenant();
      when(tenantDao.createTenant(any(Tenant.class))).thenReturn(Single.just(mockTenant));

      Tenant result = tenantService.createTenant(request).blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("Test Tenant", result.getName());
      verify(tenantDao).createTenant(any(Tenant.class));
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("test_tenant")
          .name("Test Tenant")
          .build();

      when(tenantDao.createTenant(any(Tenant.class)))
          .thenReturn(Single.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.createTenant(request).blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestGetTenant {

    @Test
    void shouldGetTenantSuccessfully() {
      Tenant mockTenant = createMockTenant();
      when(tenantDao.getTenantById("test_tenant")).thenReturn(Maybe.just(mockTenant));

      Tenant result = tenantService.getTenant("test_tenant").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
    }

    @Test
    void shouldReturnEmptyWhenTenantNotFound() {
      when(tenantDao.getTenantById("non_existent")).thenReturn(Maybe.empty());

      Tenant result = tenantService.getTenant("non_existent").blockingGet();

      assertEquals(null, result);
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(tenantDao.getTenantById(anyString()))
          .thenReturn(Maybe.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.getTenant("test_tenant").blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestGetAllActiveTenants {

    @Test
    void shouldGetAllActiveTenantsSuccessfully() {
      Tenant mockTenant = createMockTenant();
      when(tenantDao.getAllActiveTenants()).thenReturn(Flowable.just(mockTenant));

      List<Tenant> result = tenantService.getAllActiveTenants().toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("test_tenant", result.get(0).getTenantId());
    }

    @Test
    void shouldReturnEmptyListWhenNoTenants() {
      when(tenantDao.getAllActiveTenants()).thenReturn(Flowable.empty());

      List<Tenant> result = tenantService.getAllActiveTenants().toList().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(tenantDao.getAllActiveTenants())
          .thenReturn(Flowable.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.getAllActiveTenants().toList().blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestGetAllTenants {

    @Test
    void shouldGetAllTenantsSuccessfully() {
      Tenant mockTenant = createMockTenant();
      when(tenantDao.getAllTenants()).thenReturn(Flowable.just(mockTenant));

      List<Tenant> result = tenantService.getAllTenants().toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(tenantDao.getAllTenants())
          .thenReturn(Flowable.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.getAllTenants().toList().blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestUpdateTenant {

    @Test
    void shouldUpdateTenantSuccessfully() {
      UpdateTenantRequest request = UpdateTenantRequest.builder()
          .tenantId("test_tenant")
          .name("Updated Name")
          .description("Updated Description")
          .build();

      Tenant updatedTenant = createMockTenant();
      updatedTenant.setName("Updated Name");
      when(tenantDao.updateTenant(any()))
          .thenReturn(Single.just(updatedTenant));

      Tenant result = tenantService.updateTenant(request).blockingGet();

      assertNotNull(result);
      assertEquals("Updated Name", result.getName());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      UpdateTenantRequest request = UpdateTenantRequest.builder()
          .tenantId("test_tenant")
          .name("Updated Name")
          .build();

      when(tenantDao.updateTenant(any()))
          .thenReturn(Single.error(new RuntimeException("Tenant not found")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.updateTenant(request).blockingGet());
      assertTrue(ex.getMessage().contains("Tenant not found"));
    }
  }

  @Nested
  class TestDeactivateTenant {

    @Test
    void shouldDeactivateTenantSuccessfully() {
      when(tenantDao.deactivateTenant("test_tenant")).thenReturn(Completable.complete());

      tenantService.deactivateTenant("test_tenant").blockingAwait();

      verify(tenantDao).deactivateTenant("test_tenant");
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(tenantDao.deactivateTenant(anyString()))
          .thenReturn(Completable.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.deactivateTenant("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestActivateTenant {

    @Test
    void shouldActivateTenantSuccessfully() {
      when(tenantDao.activateTenant("test_tenant")).thenReturn(Completable.complete());

      tenantService.activateTenant("test_tenant").blockingAwait();

      verify(tenantDao).activateTenant("test_tenant");
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(tenantDao.activateTenant(anyString()))
          .thenReturn(Completable.error(new RuntimeException("Tenant not found")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.activateTenant("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("Tenant not found"));
    }
  }

  @Nested
  class TestDeleteTenant {

    @Test
    void shouldDeleteTenantSuccessfully() {
      when(tenantDao.deleteTenant("test_tenant")).thenReturn(Completable.complete());

      tenantService.deleteTenant("test_tenant").blockingAwait();

      verify(tenantDao).deleteTenant("test_tenant");
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(tenantDao.deleteTenant(anyString()))
          .thenReturn(Completable.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.deleteTenant("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestTenantExists {

    @Test
    void shouldReturnTrueWhenTenantExists() {
      when(tenantDao.tenantExists("test_tenant")).thenReturn(Single.just(true));

      Boolean result = tenantService.tenantExists("test_tenant").blockingGet();

      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenTenantDoesNotExist() {
      when(tenantDao.tenantExists("non_existent")).thenReturn(Single.just(false));

      Boolean result = tenantService.tenantExists("non_existent").blockingGet();

      assertEquals(false, result);
    }
  }

  // ==================== CLICKHOUSE CREDENTIALS TESTS ====================

  @Nested
  class TestCreateClickhouseCredentials {

    @Test
    void shouldCreateCredentialsSuccessfully() {
      CreateCredentialsRequest request = CreateCredentialsRequest.builder()
          .tenantId("test_tenant")
          .clickhousePassword("password123")
          .build();

      ClickhouseCredentials mockCredentials = createMockCredentials();
      when(credentialsDao.saveTenantCredentials(eq("test_tenant"), eq("password123")))
          .thenReturn(Single.just(mockCredentials));
      when(credentialsDao.insertAuditLog(eq("test_tenant"), eq(TenantAuditAction.CREDENTIALS_CREATED), eq("admin@example.com"),
          any(JsonObject.class)))
          .thenReturn(Completable.complete());

      TenantInfo result = tenantService.createClickhouseCredentials(request, "admin@example.com").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("tenant_test_tenant", result.getClickhouseUsername());
      assertTrue(result.getIsActive());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      CreateCredentialsRequest request = CreateCredentialsRequest.builder()
          .tenantId("test_tenant")
          .clickhousePassword("password123")
          .build();

      when(credentialsDao.saveTenantCredentials(anyString(), anyString()))
          .thenReturn(Single.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.createClickhouseCredentials(request, "admin").blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }

    @Test
    void shouldContinueWhenPoolCreationFails() {
      CreateCredentialsRequest request = CreateCredentialsRequest.builder()
          .tenantId("test_tenant")
          .clickhousePassword("password123")
          .build();

      ClickhouseCredentials mockCredentials = createMockCredentials();
      when(credentialsDao.saveTenantCredentials(eq("test_tenant"), eq("password123")))
          .thenReturn(Single.just(mockCredentials));
      when(credentialsDao.insertAuditLog(eq("test_tenant"), eq(TenantAuditAction.CREDENTIALS_CREATED), eq("admin@example.com"),
          any(JsonObject.class)))
          .thenReturn(Completable.complete());
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenThrow(new RuntimeException("Pool creation failed"));

      TenantInfo result = tenantService.createClickhouseCredentials(request, "admin@example.com").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
    }
  }

  @Nested
  class TestGetClickhouseCredentials {

    @Test
    void shouldGetCredentialsSuccessfully() {
      ClickhouseCredentials mockCredentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId("test_tenant"))
          .thenReturn(Single.just(mockCredentials));

      ClickhouseCredentials result = tenantService.getClickhouseCredentials("test_tenant").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("tenant_test_tenant", result.getClickhouseUsername());
    }

    @Test
    void shouldThrowExceptionWhenCredentialsNotFound() {
      when(credentialsDao.getCredentialsByTenantId("non_existent"))
          .thenReturn(Single.error(new RuntimeException("No credentials found")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.getClickhouseCredentials("non_existent").blockingGet());
      assertTrue(ex.getMessage().contains("No credentials found"));
    }
  }

  @Nested
  class TestGetAllActiveClickhouseCredentials {

    @Test
    void shouldGetAllActiveCredentialsSuccessfully() {
      ClickhouseCredentials mockCredentials = createMockCredentials();
      when(credentialsDao.getAllActiveTenantCredentials())
          .thenReturn(Flowable.just(mockCredentials));

      List<ClickhouseCredentials> result = tenantService.getAllActiveClickhouseCredentials().toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(credentialsDao.getAllActiveTenantCredentials())
          .thenReturn(Flowable.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.getAllActiveClickhouseCredentials().toList().blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestUpdateClickhouseCredentials {

    @Test
    void shouldUpdateCredentialsSuccessfully() {
      UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
          .tenantId("test_tenant")
          .newPassword("new_password")
          .reason("Password rotation")
          .build();

      ClickhouseCredentials mockCredentials = createMockCredentials();
      when(credentialsDao.updateTenantCredentials(eq("test_tenant"), eq("new_password")))
          .thenReturn(Single.just(mockCredentials));
      when(credentialsDao.insertAuditLog(eq("test_tenant"), eq(TenantAuditAction.CREDENTIALS_UPDATED), eq("admin@example.com"),
          any(JsonObject.class)))
          .thenReturn(Completable.complete());

      TenantInfo result = tenantService.updateClickhouseCredentials(request, "admin@example.com").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
    }

    @Test
    void shouldUpdateCredentialsWithNullReason() {
      UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
          .tenantId("test_tenant")
          .newPassword("new_password")
          .reason(null)
          .build();

      ClickhouseCredentials mockCredentials = createMockCredentials();
      when(credentialsDao.updateTenantCredentials(eq("test_tenant"), eq("new_password")))
          .thenReturn(Single.just(mockCredentials));
      when(credentialsDao.insertAuditLog(eq("test_tenant"), eq(TenantAuditAction.CREDENTIALS_UPDATED), eq("admin@example.com"),
          any(JsonObject.class)))
          .thenReturn(Completable.complete());

      TenantInfo result = tenantService.updateClickhouseCredentials(request, "admin@example.com").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
    }

    @Test
    void shouldContinueWhenPoolRecreationFails() {
      UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
          .tenantId("test_tenant")
          .newPassword("new_password")
          .reason("Rotation")
          .build();

      ClickhouseCredentials mockCredentials = createMockCredentials();
      when(credentialsDao.updateTenantCredentials(eq("test_tenant"), eq("new_password")))
          .thenReturn(Single.just(mockCredentials));
      when(credentialsDao.insertAuditLog(eq("test_tenant"), eq(TenantAuditAction.CREDENTIALS_UPDATED), eq("admin@example.com"),
          any(JsonObject.class)))
          .thenReturn(Completable.complete());
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenThrow(new RuntimeException("Pool creation failed"));

      TenantInfo result = tenantService.updateClickhouseCredentials(request, "admin@example.com").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      UpdateCredentialsRequest request = UpdateCredentialsRequest.builder()
          .tenantId("test_tenant")
          .newPassword("new_password")
          .build();

      when(credentialsDao.updateTenantCredentials(anyString(), anyString()))
          .thenReturn(Single.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.updateClickhouseCredentials(request, "admin").blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestDeactivateClickhouseCredentials {

    @Test
    void shouldDeactivateCredentialsSuccessfully() {
      when(credentialsDao.deactivateTenantCredentials("test_tenant"))
          .thenReturn(Completable.complete());
      when(credentialsDao.insertAuditLog(eq("test_tenant"), eq(TenantAuditAction.CREDENTIALS_DEACTIVATED), eq("admin@example.com"),
          any(JsonObject.class)))
          .thenReturn(Completable.complete());

      tenantService.deactivateClickhouseCredentials("test_tenant", "admin@example.com").blockingAwait();

      verify(credentialsDao).deactivateTenantCredentials("test_tenant");
    }

    @Test
    void shouldContinueWhenPoolCloseFailsDuringDeactivation() {
      when(credentialsDao.deactivateTenantCredentials("test_tenant"))
          .thenReturn(Completable.complete());
      when(credentialsDao.insertAuditLog(eq("test_tenant"), eq(TenantAuditAction.CREDENTIALS_DEACTIVATED), eq("admin@example.com"),
          any(JsonObject.class)))
          .thenReturn(Completable.complete());
      org.mockito.Mockito.doThrow(new RuntimeException("Pool close failed"))
          .when(poolManager).closePoolForTenant("test_tenant");

      tenantService.deactivateClickhouseCredentials("test_tenant", "admin@example.com").blockingAwait();

      verify(credentialsDao).deactivateTenantCredentials("test_tenant");
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(credentialsDao.deactivateTenantCredentials(anyString()))
          .thenReturn(Completable.error(new RuntimeException("Database error")));

      assertThrows(RuntimeException.class,
          () -> tenantService.deactivateClickhouseCredentials("test_tenant", "admin").blockingAwait());
    }
  }

  @Nested
  class TestReactivateClickhouseCredentials {

    @Test
    void shouldReactivateCredentialsSuccessfully() {
      ClickhouseCredentials mockCredentials = createMockCredentials();

      when(credentialsDao.reactivateTenantCredentials("test_tenant"))
          .thenReturn(Completable.complete());
      when(credentialsDao.getCredentialsByTenantId("test_tenant"))
          .thenReturn(Single.just(mockCredentials));
      when(credentialsDao.insertAuditLog(eq("test_tenant"), eq(TenantAuditAction.CREDENTIALS_REACTIVATED), eq("admin@example.com"),
          any(JsonObject.class)))
          .thenReturn(Completable.complete());

      TenantInfo result = tenantService.reactivateClickhouseCredentials("test_tenant", "admin@example.com").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
    }

    @Test
    void shouldContinueWhenPoolCreationFailsDuringReactivation() {
      ClickhouseCredentials mockCredentials = createMockCredentials();

      when(credentialsDao.reactivateTenantCredentials("test_tenant"))
          .thenReturn(Completable.complete());
      when(credentialsDao.getCredentialsByTenantId("test_tenant"))
          .thenReturn(Single.just(mockCredentials));
      when(credentialsDao.insertAuditLog(eq("test_tenant"), eq(TenantAuditAction.CREDENTIALS_REACTIVATED), eq("admin@example.com"),
          any(JsonObject.class)))
          .thenReturn(Completable.complete());
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenThrow(new RuntimeException("Pool creation failed"));

      TenantInfo result = tenantService.reactivateClickhouseCredentials("test_tenant", "admin@example.com").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(credentialsDao.reactivateTenantCredentials(anyString()))
          .thenReturn(Completable.error(new RuntimeException("Database error")));

      assertThrows(RuntimeException.class,
          () -> tenantService.reactivateClickhouseCredentials("test_tenant", "admin").blockingGet());
    }
  }

  // ==================== AUDIT TESTS ====================

  @Nested
  class TestGetCredentialsAuditHistory {

    @Test
    void shouldGetAuditHistorySuccessfully() {
      ClickhouseTenantCredentialAudit mockAudit = createMockAudit();
      when(credentialsDao.getAuditLogsByProjectId("test_project"))
          .thenReturn(Flowable.just(mockAudit));

      List<ClickhouseTenantCredentialAudit> result = tenantService.getCredentialsAuditHistory("test_project").toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("CREDENTIALS_CREATED", result.get(0).getAction());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(credentialsDao.getAuditLogsByProjectId(anyString()))
          .thenReturn(Flowable.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.getCredentialsAuditHistory("test_project").toList().blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  @Nested
  class TestGetRecentCredentialsAuditLogs {

    @Test
    void shouldGetRecentAuditLogsSuccessfully() {
      ClickhouseTenantCredentialAudit mockAudit = createMockAudit();
      when(credentialsDao.getRecentAuditLogs(50))
          .thenReturn(Flowable.just(mockAudit));

      List<ClickhouseTenantCredentialAudit> result = tenantService.getRecentCredentialsAuditLogs(50).toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void shouldThrowExceptionOnDaoError() {
      when(credentialsDao.getRecentAuditLogs(50))
          .thenReturn(Flowable.error(new RuntimeException("Database error")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.getRecentCredentialsAuditLogs(50).toList().blockingGet());
      assertTrue(ex.getMessage().contains("Database error"));
    }
  }

  // ==================== POOL MANAGEMENT TESTS ====================

  @Nested
  class TestRefreshConnectionPool {

    @Test
    void shouldRefreshPoolSuccessfully() {
      ClickhouseCredentials mockCredentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId("test_tenant"))
          .thenReturn(Single.just(mockCredentials));
      doNothing().when(poolManager).closePoolForTenant("test_tenant");

      tenantService.refreshConnectionPool("test_tenant").blockingAwait();

      verify(poolManager).closePoolForTenant("test_tenant");
      verify(poolManager).getPoolForTenant(eq("test_tenant"), eq("tenant_test_tenant"), anyString());
    }

    @Test
    void shouldThrowExceptionOnError() {
      when(credentialsDao.getCredentialsByTenantId("test_tenant"))
          .thenReturn(Single.error(new RuntimeException("Credentials not found")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.refreshConnectionPool("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("Credentials not found"));
    }

    @Test
    void shouldThrowExceptionWhenPoolCreationFails() {
      ClickhouseCredentials mockCredentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId("test_tenant"))
          .thenReturn(Single.just(mockCredentials));
      doNothing().when(poolManager).closePoolForTenant("test_tenant");
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenThrow(new RuntimeException("Pool creation failed"));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantService.refreshConnectionPool("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("Pool creation failed"));
    }
  }

  @Nested
  class TestGetPoolStatistics {

    @Test
    void shouldGetPoolStatisticsSuccessfully() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics mockStats =
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("test_tenant", 5, 10, true);
      when(poolManager.getPoolStatistics("test_tenant")).thenReturn(mockStats);

      ClickhouseTenantConnectionPoolManager.PoolStatistics result =
          tenantService.getPoolStatistics("test_tenant");

      assertNotNull(result);
      assertEquals("test_tenant", result.tenantId);
      assertEquals(5, result.activeConnections);
      assertEquals(10, result.maxConnections);
      assertTrue(result.isActive);
    }

    @Test
    void shouldReturnNullWhenPoolNotFound() {
      when(poolManager.getPoolStatistics("non_existent")).thenReturn(null);

      ClickhouseTenantConnectionPoolManager.PoolStatistics result =
          tenantService.getPoolStatistics("non_existent");

      assertEquals(null, result);
    }
  }
}
