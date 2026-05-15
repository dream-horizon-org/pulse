package org.dreamhorizon.pulseserver.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.WebApplicationException;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.dto.ProjectCreationResult;
import org.dreamhorizon.pulseserver.dto.request.ReqUserInfo;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.ProjectService;
import org.dreamhorizon.pulseserver.service.tenant.dto.TenantWithProjectResult;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateTenantRequest;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.dreamhorizon.pulseserver.service.tier.models.TierInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

  @Mock
  TenantDao tenantDao;

  @Mock
  ClickhouseProjectConnectionPoolManager poolManager;

  @Mock
  OpenFgaService openFgaService;

  @Mock
  TierService tierService;

  @Mock
  ProjectService projectService;

  TenantService tenantService;

  @BeforeEach
  void setUp() {
    tenantService = new TenantService(tenantDao, poolManager, openFgaService, tierService, projectService);
  }

  private Tenant createTenant(String tenantId, String name) {
    return Tenant.builder()
        .tenantId(tenantId)
        .name(name)
        .description("Test description")
        .tierId(1)
        .isActive(true)
        .build();
  }

  @Nested
  class CreateTenant {

    @Test
    void shouldCreateTenantSuccessfully() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("tenant-1")
          .name("Test Org")
          .description("A test organization")
          .gcpTenantId("gcp-123")
          .domainName("test.com")
          .build();

      Tenant expected = Tenant.builder()
          .tenantId("tenant-1")
          .name("Test Org")
          .description("A test organization")
          .gcpTenantId("gcp-123")
          .domainName("test.com")
          .isActive(true)
          .build();

      when(tenantDao.createTenant(any(Tenant.class))).thenReturn(Single.just(expected));

      Tenant result = tenantService.createTenant(request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTenantId()).isEqualTo("tenant-1");
      assertThat(result.getName()).isEqualTo("Test Org");
      assertThat(result.getIsActive()).isTrue();

      ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
      verify(tenantDao).createTenant(captor.capture());
      assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-1");
      assertThat(captor.getValue().getName()).isEqualTo("Test Org");
      assertThat(captor.getValue().getIsActive()).isTrue();

      verify(openFgaService, never()).linkTenantToSystem(anyString());
    }

    @Test
    void shouldLinkTenantToSystemWhenOpenFgaEnabled() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("tenant-1")
          .name("Test Org")
          .description("A test organization")
          .build();

      Tenant expected = Tenant.builder()
          .tenantId("tenant-1")
          .name("Test Org")
          .description("A test organization")
          .isActive(true)
          .build();

      when(tenantDao.createTenant(any(Tenant.class))).thenReturn(Single.just(expected));
      when(openFgaService.isEnabled()).thenReturn(true);
      when(openFgaService.linkTenantToSystem("tenant-1")).thenReturn(Completable.complete());

      tenantService.createTenant(request).blockingGet();

      verify(openFgaService).linkTenantToSystem("tenant-1");
    }

    @Test
    void shouldPropagateErrorWhenLinkTenantToSystemFails() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("tenant-1")
          .name("Test Org")
          .build();

      Tenant expected = Tenant.builder()
          .tenantId("tenant-1")
          .name("Test Org")
          .isActive(true)
          .build();

      when(tenantDao.createTenant(any(Tenant.class))).thenReturn(Single.just(expected));
      when(openFgaService.isEnabled()).thenReturn(true);
      when(openFgaService.linkTenantToSystem("tenant-1"))
          .thenReturn(Completable.error(new RuntimeException("OpenFGA write failed")));

      tenantService.createTenant(request)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("OpenFGA write failed"));
    }

    @Test
    void shouldPropagateErrorFromDao() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("tenant-1")
          .name("Test Org")
          .build();

      when(tenantDao.createTenant(any(Tenant.class)))
          .thenReturn(Single.error(new RuntimeException("DB error")));

      tenantService.createTenant(request)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("DB error"));
    }
  }

  @Nested
  class GetTenant {

    @Test
    void shouldReturnTenantWhenFound() {
      Tenant tenant = createTenant("tenant-1", "Test Org");
      when(tenantDao.getTenantById("tenant-1")).thenReturn(Maybe.just(tenant));

      Tenant result = tenantService.getTenant("tenant-1").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTenantId()).isEqualTo("tenant-1");
      verify(tenantDao).getTenantById("tenant-1");
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
      when(tenantDao.getTenantById("nonexistent")).thenReturn(Maybe.empty());

      tenantService.getTenant("nonexistent")
          .test()
          .assertNoValues()
          .assertComplete();
    }

    @Test
    void shouldPropagateErrorFromDao() {
      when(tenantDao.getTenantById("tenant-1"))
          .thenReturn(Maybe.error(new RuntimeException("DB error")));

      tenantService.getTenant("tenant-1")
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class GetAllActiveTenants {

    @Test
    void shouldReturnActiveTenants() {
      Tenant t1 = createTenant("tenant-1", "Org 1");
      Tenant t2 = createTenant("tenant-2", "Org 2");
      when(tenantDao.getAllActiveTenants()).thenReturn(Flowable.just(t1, t2));

      tenantService.getAllActiveTenants()
          .test()
          .assertValueCount(2)
          .assertValues(t1, t2)
          .assertComplete();
    }

    @Test
    void shouldReturnEmptyWhenNoActiveTenants() {
      when(tenantDao.getAllActiveTenants()).thenReturn(Flowable.empty());

      tenantService.getAllActiveTenants()
          .test()
          .assertNoValues()
          .assertComplete();
    }

    @Test
    void shouldPropagateErrorFromDao() {
      when(tenantDao.getAllActiveTenants())
          .thenReturn(Flowable.error(new RuntimeException("DB error")));

      tenantService.getAllActiveTenants()
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class GetAllTenants {

    @Test
    void shouldReturnAllTenants() {
      Tenant t1 = createTenant("tenant-1", "Org 1");
      when(tenantDao.getAllTenants()).thenReturn(Flowable.just(t1));

      tenantService.getAllTenants()
          .test()
          .assertValueCount(1)
          .assertValue(t1)
          .assertComplete();
    }

    @Test
    void shouldPropagateErrorFromDao() {
      when(tenantDao.getAllTenants())
          .thenReturn(Flowable.error(new RuntimeException("DB error")));

      tenantService.getAllTenants()
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class UpdateTenant {

    @Test
    void shouldUpdateTenantSuccessfully() {
      UpdateTenantRequest request = UpdateTenantRequest.builder()
          .tenantId("tenant-1")
          .name("Updated Org")
          .description("Updated desc")
          .build();

      Tenant updated = Tenant.builder()
          .tenantId("tenant-1")
          .name("Updated Org")
          .description("Updated desc")
          .build();

      when(tenantDao.updateTenant(any(Tenant.class))).thenReturn(Single.just(updated));

      Tenant result = tenantService.updateTenant(request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo("Updated Org");
      verify(tenantDao).updateTenant(any(Tenant.class));
    }

    @Test
    void shouldPropagateErrorFromDao() {
      UpdateTenantRequest request = UpdateTenantRequest.builder()
          .tenantId("tenant-1")
          .name("Updated")
          .build();

      when(tenantDao.updateTenant(any(Tenant.class)))
          .thenReturn(Single.error(new RuntimeException("DB error")));

      tenantService.updateTenant(request)
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class DeactivateTenant {

    @Test
    void shouldDeactivateTenantSuccessfully() {
      when(tenantDao.deactivateTenant("tenant-1")).thenReturn(Completable.complete());

      tenantService.deactivateTenant("tenant-1").blockingAwait();

      verify(tenantDao).deactivateTenant("tenant-1");
    }

    @Test
    void shouldPropagateErrorFromDao() {
      when(tenantDao.deactivateTenant("tenant-1"))
          .thenReturn(Completable.error(new RuntimeException("DB error")));

      tenantService.deactivateTenant("tenant-1")
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class ActivateTenant {

    @Test
    void shouldActivateTenantSuccessfully() {
      when(tenantDao.activateTenant("tenant-1")).thenReturn(Completable.complete());

      tenantService.activateTenant("tenant-1").blockingAwait();

      verify(tenantDao).activateTenant("tenant-1");
    }

    @Test
    void shouldPropagateErrorFromDao() {
      when(tenantDao.activateTenant("tenant-1"))
          .thenReturn(Completable.error(new RuntimeException("DB error")));

      tenantService.activateTenant("tenant-1")
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class UpdateTenantTier {

    private TierInfo activeTierInfo() {
      return TierInfo.builder().tierId(2).name("enterprise").isActive(true).build();
    }

    @Test
    void shouldUpdateTenantTierSuccessfully() {
      Tenant tenant = createTenant("tenant-1", "Test Org");
      Tenant updated = createTenant("tenant-1", "Test Org");
      updated.setTierId(2);

      when(tenantDao.getTenantById("tenant-1")).thenReturn(Maybe.just(tenant));
      when(tierService.getTierById(2)).thenReturn(Maybe.just(activeTierInfo()));
      when(tenantDao.updateTenantTier(tenant, 2)).thenReturn(Single.just(updated));

      Tenant result = tenantService.updateTenantTier("tenant-1", 2).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTierId()).isEqualTo(2);
      verify(tenantDao).updateTenantTier(tenant, 2);
    }

    @Test
    void shouldReturn404WhenTenantNotFound() {
      when(tenantDao.getTenantById("unknown")).thenReturn(Maybe.empty());

      tenantService.updateTenantTier("unknown", 2)
          .test()
          .assertError(e -> e instanceof WebApplicationException
              && ((WebApplicationException) e).getResponse().getStatus() == 404);
    }

    @Test
    void shouldReturn404WhenTierNotFound() {
      Tenant tenant = createTenant("tenant-1", "Test Org");
      when(tenantDao.getTenantById("tenant-1")).thenReturn(Maybe.just(tenant));
      when(tierService.getTierById(999)).thenReturn(Maybe.empty());

      tenantService.updateTenantTier("tenant-1", 999)
          .test()
          .assertError(e -> e instanceof WebApplicationException
              && ((WebApplicationException) e).getResponse().getStatus() == 404);
    }

    @Test
    void shouldReturn400WhenTierIsInactive() {
      Tenant tenant = createTenant("tenant-1", "Test Org");
      TierInfo inactiveTier = TierInfo.builder().tierId(2).name("enterprise").isActive(false).build();

      when(tenantDao.getTenantById("tenant-1")).thenReturn(Maybe.just(tenant));
      when(tierService.getTierById(2)).thenReturn(Maybe.just(inactiveTier));

      tenantService.updateTenantTier("tenant-1", 2)
          .test()
          .assertError(e -> e instanceof WebApplicationException
              && ((WebApplicationException) e).getResponse().getStatus() == 400);
    }

    @Test
    void shouldPropagateErrorFromDao() {
      Tenant tenant = createTenant("tenant-1", "Test Org");

      when(tenantDao.getTenantById("tenant-1")).thenReturn(Maybe.just(tenant));
      when(tierService.getTierById(2)).thenReturn(Maybe.just(activeTierInfo()));
      when(tenantDao.updateTenantTier(tenant, 2))
          .thenReturn(Single.error(new RuntimeException("DB error")));

      tenantService.updateTenantTier("tenant-1", 2)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("DB error"));
    }
  }

  @Nested
  class CreateTenantWithProject {

    private ReqUserInfo ownerInfo() {
      return ReqUserInfo.builder()
          .userId("user-1")
          .email("owner@example.com")
          .name("Owner")
          .build();
    }

    @Test
    void shouldCreateTenantAndProjectAndReturnAllFields() {
      Tenant tenant = Tenant.builder()
          .tenantId("tenant-generated")
          .name("My Org")
          .isActive(true)
          .build();
      Project project = Project.builder()
          .projectId("my-project-abc12345")
          .tenantId("tenant-generated")
          .name("My Project")
          .isActive(true)
          .build();
      ProjectCreationResult projectResult = ProjectCreationResult.builder()
          .project(project)
          .rawApiKey("raw-api-key-123")
          .build();

      when(tenantDao.createTenant(any(Tenant.class))).thenReturn(Single.just(tenant));
      when(openFgaService.isEnabled()).thenReturn(true);
      when(openFgaService.linkTenantToSystem(any())).thenReturn(Completable.complete());
      when(projectService.createProject(any(), any(), any(), any())).thenReturn(Single.just(projectResult));
      when(openFgaService.assignTenantRole(eq("user-1"), any(), eq("admin"))).thenReturn(Completable.complete());

      TenantWithProjectResult result = tenantService.createTenantWithProject(
          ownerInfo(), "My Org", "My Project", "desc", "project desc").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTenantId()).startsWith("tenant-");
      assertThat(result.getTenantId()).contains("-");
      assertThat(result.getProjectId()).isEqualTo("my-project-abc12345");
      assertThat(result.getRawApiKey()).isEqualTo("raw-api-key-123");

      verify(openFgaService).assignTenantRole(eq("user-1"), any(), eq("admin"));
    }

    @Test
    void shouldPropagateErrorWhenProjectCreationFails() {
      Tenant tenant = Tenant.builder()
          .tenantId("tenant-abc")
          .name("My Org")
          .isActive(true)
          .build();

      when(tenantDao.createTenant(any(Tenant.class))).thenReturn(Single.just(tenant));
      when(openFgaService.isEnabled()).thenReturn(true);
      when(openFgaService.linkTenantToSystem(any())).thenReturn(Completable.complete());
      when(projectService.createProject(any(), any(), any(), any()))
          .thenReturn(Single.error(new RuntimeException("Project creation failed")));

      tenantService.createTenantWithProject(ownerInfo(), "My Org", "My Project", null, null)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("Project creation failed"));
    }

    @Test
    void shouldSkipOpenFgaTenantRoleAssignmentWhenOpenFgaDisabled() {
      Tenant tenant = Tenant.builder()
          .tenantId("tenant-abc")
          .name("My Org")
          .isActive(true)
          .build();
      Project project = Project.builder()
          .projectId("proj-1")
          .name("My Project")
          .isActive(true)
          .build();
      ProjectCreationResult projectResult = ProjectCreationResult.builder()
          .project(project)
          .rawApiKey("key")
          .build();

      when(tenantDao.createTenant(any(Tenant.class))).thenReturn(Single.just(tenant));
      when(openFgaService.isEnabled()).thenReturn(false);
      when(projectService.createProject(any(), any(), any(), any())).thenReturn(Single.just(projectResult));

      TenantWithProjectResult result = tenantService.createTenantWithProject(
          ownerInfo(), "My Org", "My Project", null, null).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("proj-1");
      verify(openFgaService, never()).assignTenantRole(any(), any(), any());
    }
  }
}
