package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
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

@ExtendWith(MockitoExtension.class)
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

  @BeforeEach
  void setUp() {
    userProjectsService = new UserProjectsService(openFgaService, projectDao, tenantDao);
  }

  private Tenant createTenant(String tenantId, String name) {
    return Tenant.builder()
        .tenantId(tenantId)
        .name(name)
        .isActive(true)
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
  class GetUserProjects {

    @Test
    void shouldReturnProjectsForUser() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME);
      Project project1 = createProject("proj-1", TENANT_ID, "Project 1", "Desc 1");
      Project project2 = createProject("proj-2", TENANT_ID, "Project 2", "Desc 2");

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(projectDao.getProjectsByTenantId(TENANT_ID))
          .thenReturn(Flowable.just(project1, project2));

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
      assertThat(result.getTenantName()).isEqualTo(TENANT_NAME);
      assertThat(result.getProjects()).hasSize(2);
      assertThat(result.getRedirectTo()).isEqualTo("/project-selection");

      assertThat(result.getProjects().get(0).getProjectId()).isEqualTo("proj-1");
      assertThat(result.getProjects().get(0).getName()).isEqualTo("Project 1");
      assertThat(result.getProjects().get(0).getDescription()).isEqualTo("Desc 1");
      assertThat(result.getProjects().get(0).getIsActive()).isTrue();
      assertThat(result.getProjects().get(0).getRole()).isEqualTo("admin");

      assertThat(result.getProjects().get(1).getProjectId()).isEqualTo("proj-2");
      assertThat(result.getProjects().get(1).getName()).isEqualTo("Project 2");
      assertThat(result.getProjects().get(1).getRole()).isEqualTo("admin");

      verify(tenantDao).getTenantById(TENANT_ID);
      verify(projectDao).getProjectsByTenantId(TENANT_ID);
    }

    @Test
    void shouldRedirectToProjectForSingleProject() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME);
      Project project = createProject("proj-single", TENANT_ID, "Single Project", null);

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(projectDao.getProjectsByTenantId(TENANT_ID)).thenReturn(Flowable.just(project));

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjects()).hasSize(1);
      assertThat(result.getRedirectTo()).isEqualTo("/projects/proj-single");
    }

    @Test
    void shouldReturnNullRedirectWhenNoProjects() {
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME);

      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(projectDao.getProjectsByTenantId(TENANT_ID)).thenReturn(Flowable.empty());

      UserProjectsService.UserProjectsResult result =
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjects()).isEmpty();
      assertThat(result.getRedirectTo()).isNull();
    }

    @Test
    void shouldFailWhenTenantNotFound() {
      when(tenantDao.getTenantById(TENANT_ID)).thenReturn(Maybe.empty());

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          userProjectsService.getUserProjects(USER_ID, TENANT_ID).blockingGet());

      assertThat(ex.getMessage()).contains("Tenant not found");
      assertThat(ex.getMessage()).contains(TENANT_ID);
    }
  }
}
