package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Optional;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProjectsServiceTest {

  @Mock
  OpenFgaService openFgaService;

  @Mock
  ProjectDao projectDao;

  @Mock
  TenantDao tenantDao;

  UserProjectsService userProjectsService;

  private static final String USER_ID = "user-1";
  private static final String TENANT_ID = "tenant-1";
  private static final String TENANT_NAME = "Test Tenant";
  private static final int ENTERPRISE_TIER_ID = 2;

  @BeforeEach
  void setUp() {
    userProjectsService = new UserProjectsService(openFgaService, projectDao, tenantDao);
    when(openFgaService.isEnabled()).thenReturn(false);
  }

  private Tenant createTenant(String tenantId, String name, Integer tierId) {
    return Tenant.builder()
        .tenantId(tenantId)
        .name(name)
        .isActive(true)
        .tierId(tierId)
        .build();
  }

  private Project createProject(String projectId, String tenantId, String name, String description) {
    return Project.builder()
        .projectId(projectId)
        .tenantId(tenantId)
        .name(name)
        .description(description)
        .isActive(true)
        .build();
  }

  @Nested
  class OpenFgaDisabledLegacy {

    @Test
    void shouldReturnAllTenantProjectsWithAdminRole() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME, ENTERPRISE_TIER_ID);
      Project project1 = createProject("proj-1", TENANT_ID, "Project 1", "Desc 1");
      Project project2 = createProject("proj-2", TENANT_ID, "Project 2", "Desc 2");

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(projectDao.getProjectsByTenantId(TENANT_ID))
          .thenReturn(Flowable.just(project1, project2));

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result.getProjects()).hasSize(2);
      assertThat(result.getRedirectTo()).isEqualTo("/project-selection");
      assertThat(result.getProjects().get(0).getRole()).isEqualTo("admin");
      assertThat(result.getProjects().get(1).getRole()).isEqualTo("admin");
      verify(projectDao).getProjectsByTenantId(TENANT_ID);
    }

    @Test
    void shouldRedirectToProjectForSingleProject() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME, ENTERPRISE_TIER_ID);
      Project project = createProject("proj-single", TENANT_ID, "Single Project", null);

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(projectDao.getProjectsByTenantId(TENANT_ID)).thenReturn(Flowable.just(project));

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result.getProjects()).hasSize(1);
      assertThat(result.getRedirectTo()).isEqualTo("/projects/proj-single");
    }

    @Test
    void shouldReturnNullRedirectWhenNoProjects() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME, ENTERPRISE_TIER_ID);

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(projectDao.getProjectsByTenantId(TENANT_ID)).thenReturn(Flowable.empty());

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result.getProjects()).isEmpty();
      assertThat(result.getRedirectTo()).isNull();
    }

    @Test
    void freeTierReturnsOnlyFirstProjectAndRedirectsThere() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME, 1);
      Project p1 = createProject("proj-1", TENANT_ID, "P1", null);
      Project p2 = createProject("proj-2", TENANT_ID, "P2", null);

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(projectDao.getProjectsByTenantId(TENANT_ID)).thenReturn(Flowable.just(p1, p2));

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result.getProjects()).hasSize(1);
      assertThat(result.getProjects().get(0).getProjectId()).isEqualTo("proj-1");
      assertThat(result.getRedirectTo()).isEqualTo("/projects/proj-1");
    }
  }

  @Nested
  class OpenFgaEnabledTenantAdmin {

    @BeforeEach
    void fgaOn() {
      when(openFgaService.isEnabled()).thenReturn(true);
    }

    @Test
    void listsAllDbProjectsWithRolesFromFga() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME, ENTERPRISE_TIER_ID);
      Project project1 = createProject("proj-1", TENANT_ID, "Project 1", "D1");
      Project project2 = createProject("proj-2", TENANT_ID, "Project 2", "D2");

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(openFgaService.isTenantAdmin(USER_ID, TENANT_ID)).thenReturn(Single.just(true));
      when(projectDao.getProjectsByTenantId(TENANT_ID))
          .thenReturn(Flowable.just(project1, project2));
      when(openFgaService.getUserRoleInProject(USER_ID, "proj-1"))
          .thenReturn(Single.just(Optional.of("editor")));
      when(openFgaService.getUserRoleInProject(USER_ID, "proj-2"))
          .thenReturn(Single.just(Optional.empty()));

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result.getProjects()).hasSize(2);
      assertThat(result.getProjects().get(0).getRole()).isEqualTo("editor");
      assertThat(result.getProjects().get(1).getRole()).isEqualTo("organization_admin");
      assertThat(result.getRedirectTo()).isEqualTo("/project-selection");
    }

    @Test
    void emptyTenantHasNoRedirect() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME, ENTERPRISE_TIER_ID);

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(openFgaService.isTenantAdmin(USER_ID, TENANT_ID)).thenReturn(Single.just(true));
      when(projectDao.getProjectsByTenantId(TENANT_ID)).thenReturn(Flowable.empty());

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result.getProjects()).isEmpty();
      assertThat(result.getRedirectTo()).isNull();
    }
  }

  @Nested
  class OpenFgaEnabledMember {

    @BeforeEach
    void fgaOn() {
      when(openFgaService.isEnabled()).thenReturn(true);
      when(openFgaService.isTenantAdmin(USER_ID, TENANT_ID)).thenReturn(Single.just(false));
    }

    @Test
    void listsOnlyProjectsInTenantWithExplicitTuples() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME, ENTERPRISE_TIER_ID);
      Project otherTenant = createProject("proj-x", "other-tenant", "X", null);
      Project inTenant = createProject("proj-1", TENANT_ID, "In", null);

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(openFgaService.getUserProjects(USER_ID))
          .thenReturn(Single.just(List.of("proj-x", "proj-1")));
      when(projectDao.getProjectByProjectId("proj-x")).thenReturn(Maybe.just(otherTenant));
      when(projectDao.getProjectByProjectId("proj-1")).thenReturn(Maybe.just(inTenant));
      when(openFgaService.getUserRoleInProject(USER_ID, "proj-1"))
          .thenReturn(Single.just(Optional.of("viewer")));

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result.getProjects()).hasSize(1);
      assertThat(result.getProjects().get(0).getProjectId()).isEqualTo("proj-1");
      assertThat(result.getProjects().get(0).getRole()).isEqualTo("viewer");
      assertThat(result.getRedirectTo()).isEqualTo("/projects/proj-1");
    }

    @Test
    void noProjectsWhenFgaListEmpty() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME, ENTERPRISE_TIER_ID);

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(openFgaService.getUserProjects(USER_ID)).thenReturn(Single.just(List.of()));

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result.getProjects()).isEmpty();
      assertThat(result.getRedirectTo()).isNull();
    }

    @Test
    void freeTierCapsFilteredListToFirstProject() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME, 1);
      Project p1 = createProject("proj-1", TENANT_ID, "P1", null);
      Project p2 = createProject("proj-2", TENANT_ID, "P2", null);

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(openFgaService.getUserProjects(USER_ID))
          .thenReturn(Single.just(List.of("proj-1", "proj-2")));
      when(projectDao.getProjectByProjectId(anyString()))
          .thenAnswer(
              inv -> {
                String id = inv.getArgument(0);
                return Maybe.just(
                    "proj-1".equals(id)
                        ? p1
                        : "proj-2".equals(id) ? p2 : createProject(id, TENANT_ID, id, null));
              });
      when(openFgaService.getUserRoleInProject(eq(USER_ID), eq("proj-1")))
          .thenReturn(Single.just(Optional.of("admin")));
      when(openFgaService.getUserRoleInProject(eq(USER_ID), eq("proj-2")))
          .thenReturn(Single.just(Optional.of("viewer")));

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result.getProjects()).hasSize(1);
      assertThat(result.getProjects().get(0).getProjectId()).isEqualTo("proj-1");
      assertThat(result.getRedirectTo()).isEqualTo("/projects/proj-1");
    }
  }

  @Nested
  class TenantNotFound {

    @Test
    void shouldFailWhenTenantNotFound() {
      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.empty());

      RuntimeException ex =
          assertThrows(
              RuntimeException.class,
              () -> userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet());

      assertThat(ex.getMessage()).contains("Tenant not found");
      assertThat(ex.getMessage()).contains(TENANT_ID);
    }
  }
}
