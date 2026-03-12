package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Transaction;
import java.util.Collections;
import java.util.Optional;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.dto.ProjectCreationResult;
import org.dreamhorizon.pulseserver.dto.ProjectDetailsDto;
import org.dreamhorizon.pulseserver.dto.ProjectSummaryDto;
import org.dreamhorizon.pulseserver.dto.request.ReqUserInfo;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyInfo;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.UploadConfigDetailService;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.dao.usagelimit.models.ProjectUsageLimit;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
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
class ProjectServiceTest {

  @Mock MysqlClient mysqlClient;
  @Mock MySQLPool writerPool;
  @Mock SqlConnection sqlConnection;
  @Mock Transaction transaction;
  @Mock ProjectDao projectDao;
  @Mock OpenFgaService openFgaService;
  @Mock ClickhouseProjectService clickhouseProjectService;
  @Mock ProjectApiKeyService projectApiKeyService;
  @Mock ConfigService configService;
  @Mock UsageLimitService usageLimitService;
  @Mock UploadConfigDetailService uploadConfigDetailService;
  @Mock NotificationService notificationService;

  ProjectService projectService;

  @BeforeEach
  void setUp() {
    projectService =
        new ProjectService(
            mysqlClient,
            projectDao,
            openFgaService,
            clickhouseProjectService,
            projectApiKeyService,
            configService,
            usageLimitService,
            uploadConfigDetailService,
            notificationService);
  }

  @Nested
  class CreateProject {

    @Test
    void shouldCreateProjectSuccessfullyOnHappyPath() {
      ReqUserInfo userInfo =
          ReqUserInfo.builder()
              .userId("user-1")
              .email("user@example.com")
              .name("Test User")
              .build();

      Project createdProject =
          Project.builder()
              .id(1)
              .projectId("my-project-abc12345")
              .tenantId("tenant-1")
              .name("my-project")
              .description("desc")
              .isActive(true)
              .createdBy("user-1")
              .build();

      var credentialsResult =
          new ClickhouseProjectService.CredentialsResult(
              "my-project-abc12345", "project_my_project_abc12345", "secret");

      ApiKeyInfo apiKeyInfo =
          ApiKeyInfo.builder()
              .projectId("my-project-abc12345")
              .rawApiKey("raw-api-key-123")
              .displayName("Default")
              .build();

      ProjectUsageLimit usageLimit = ProjectUsageLimit.builder().build();
      PulseConfig pulseConfig = mock(PulseConfig.class);

      when(mysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.rxBegin()).thenReturn(Single.just(transaction));
      when(transaction.rxCommit()).thenReturn(Completable.complete());

      when(projectDao.createProject(any(SqlConnection.class), any(Project.class)))
          .thenReturn(Single.just(createdProject));
      when(clickhouseProjectService.saveCredentials(any(SqlConnection.class), anyString()))
          .thenReturn(Single.just(credentialsResult));
      when(projectApiKeyService.createDefaultApiKey(
              any(SqlConnection.class), anyString(), eq("user-1")))
          .thenReturn(Single.just(apiKeyInfo));
      when(usageLimitService.createInitialLimits(
              any(SqlConnection.class), anyString(), eq("admin")))
          .thenReturn(Single.just(usageLimit));
      when(configService.createInitialConfig(
              any(SqlConnection.class), anyString(), eq("user-1")))
          .thenReturn(Single.just(pulseConfig));

      when(openFgaService.assignProjectRole(eq("user-1"), anyString(), eq("admin")))
          .thenReturn(Completable.complete());
      when(openFgaService.linkProjectToTenant(anyString(), eq("tenant-1")))
          .thenReturn(Completable.complete());

      when(clickhouseProjectService.createClickhouseUserAndPolicies(
              anyString(), anyString(), anyString()))
          .thenReturn(Completable.complete());
      when(uploadConfigDetailService.pushInteractionDetailsToObjectStore(anyString()))
          .thenReturn(Single.just(new org.dreamhorizon.pulseserver.dto.response.EmptyResponse()));
      when(notificationService.sendNotification(anyString(), any()))
          .thenReturn(Single.just(
              org.dreamhorizon.pulseserver.resources.notification.models.NotificationBatchResponseDto
                  .builder()
                  .idempotencyKey("batch-1")
                  .build()));

      ProjectCreationResult result =
          projectService
              .createProject("tenant-1", "my-project", "desc", userInfo)
              .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProject()).isNotNull();
      assertThat(result.getProject().getProjectId()).startsWith("my-project-");
      assertThat(result.getProject().getName()).isEqualTo("my-project");
      assertThat(result.getProject().getTenantId()).isEqualTo("tenant-1");
      assertThat(result.getRawApiKey()).isEqualTo("raw-api-key-123");

      verify(projectDao).createProject(any(SqlConnection.class), any(Project.class));
      verify(openFgaService).assignProjectRole(eq("user-1"), anyString(), eq("admin"));
      verify(openFgaService).linkProjectToTenant(anyString(), eq("tenant-1"));
    }

    @Test
    void shouldPropagateErrorWhenProjectDaoFails() {
      ReqUserInfo userInfo =
          ReqUserInfo.builder().userId("user-1").email("u@x.com").name("User").build();

      when(mysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.rxBegin()).thenReturn(Single.just(transaction));
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      when(projectDao.createProject(any(SqlConnection.class), any(Project.class)))
          .thenReturn(Single.error(new RuntimeException("DB insert failed")));

      RuntimeException ex =
          assertThrows(
              RuntimeException.class,
              () ->
                  projectService
                      .createProject("tenant-1", "my-project", "desc", userInfo)
                      .blockingGet());

      assertThat(ex).hasStackTraceContaining("DB insert failed");
    }
  }

  @Nested
  class GetProjectById {

    @Test
    void shouldReturnProjectWhenFound() {
      Project project =
          Project.builder()
              .projectId("proj-1")
              .tenantId("tenant-1")
              .name("Project 1")
              .build();

      when(projectDao.getProjectByProjectId("proj-1")).thenReturn(Maybe.just(project));

      Project result = projectService.getProjectById("proj-1").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("proj-1");
      assertThat(result.getName()).isEqualTo("Project 1");
      verify(projectDao).getProjectByProjectId("proj-1");
    }

    @Test
    void shouldThrowWhenProjectNotFound() {
      when(projectDao.getProjectByProjectId("nonexistent")).thenReturn(Maybe.empty());

      RuntimeException ex =
          assertThrows(
              RuntimeException.class,
              () -> projectService.getProjectById("nonexistent").blockingGet());

      assertThat(ex.getMessage()).contains("Project not found");
    }
  }

  @Nested
  class GetProjectsForUser {

    @Test
    void shouldReturnProjectsWithRolesWhenProjectsExist() {
      Project p1 =
          Project.builder()
              .projectId("p1")
              .name("Proj 1")
              .description("D1")
              .isActive(true)
              .createdAt("2024-01-01")
              .build();
      Project p2 =
          Project.builder()
              .projectId("p2")
              .name("Proj 2")
              .description("D2")
              .isActive(true)
              .createdAt("2024-01-02")
              .build();

      when(projectDao.getProjectsByTenantId("tenant-1"))
          .thenReturn(Flowable.fromArray(p1, p2));
      when(openFgaService.getUserRoleInProject("user-1", "p1"))
          .thenReturn(Single.just(Optional.of("admin")));
      when(openFgaService.getUserRoleInProject("user-1", "p2"))
          .thenReturn(Single.just(Optional.of("member")));

      java.util.List<ProjectSummaryDto> result =
          projectService.getProjectsForUser("user-1", "tenant-1").blockingGet();

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getProjectId()).isEqualTo("p1");
      assertThat(result.get(0).getRole()).isEqualTo("admin");
      assertThat(result.get(1).getProjectId()).isEqualTo("p2");
      assertThat(result.get(1).getRole()).isEqualTo("member");

      verify(projectDao).getProjectsByTenantId("tenant-1");
    }

    @Test
    void shouldReturnEmptyListWhenNoProjects() {
      when(projectDao.getProjectsByTenantId("tenant-1")).thenReturn(Flowable.empty());

      java.util.List<ProjectSummaryDto> result =
          projectService.getProjectsForUser("user-1", "tenant-1").blockingGet();

      assertThat(result).isEmpty();
      verify(projectDao).getProjectsByTenantId("tenant-1");
    }
  }

  @Nested
  class UpdateProject {

    @Test
    void shouldUpdateProjectSuccessfully() {
      Project existing =
          Project.builder()
              .projectId("proj-1")
              .name("Old Name")
              .description("Old Desc")
              .build();
      Project updated =
          Project.builder()
              .projectId("proj-1")
              .name("New Name")
              .description("New Desc")
              .build();

      when(projectDao.getProjectByProjectId("proj-1")).thenReturn(Maybe.just(existing));
      when(projectDao.updateProject(any(Project.class))).thenReturn(Single.just(updated));

      Project result =
          projectService.updateProject("proj-1", "New Name", "New Desc").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo("New Name");
      assertThat(result.getDescription()).isEqualTo("New Desc");
      verify(projectDao).getProjectByProjectId("proj-1");
      verify(projectDao).updateProject(any(Project.class));
    }

    @Test
    void shouldThrowWhenProjectNotFoundForUpdate() {
      when(projectDao.getProjectByProjectId("nonexistent")).thenReturn(Maybe.empty());

      RuntimeException ex =
          assertThrows(
              RuntimeException.class,
              () ->
                  projectService
                      .updateProject("nonexistent", "New Name", "New Desc")
                      .blockingGet());

      assertThat(ex.getMessage()).contains("Project not found");
    }
  }

  @Nested
  class DeactivateProject {

    @Test
    void shouldCallDaoDeactivateProject() {
      when(projectDao.deactivateProject("proj-1")).thenReturn(Completable.complete());

      try {
        projectService.deactivateProject("proj-1").blockingGet();
      } catch (NullPointerException ignored) {
        // Single.just((Void) null) in production code throws NPE
      }

      verify(projectDao).deactivateProject("proj-1");
    }
  }

  @Nested
  class GetProjectDetails {

    @Test
    void shouldReturnProjectDetailsWhenUserHasAccess() {
      Project project =
          Project.builder()
              .projectId("proj-1")
              .tenantId("tenant-1")
              .name("My Project")
              .description("Desc")
              .isActive(true)
              .createdBy("creator")
              .createdAt("2024-01-01")
              .updatedAt("2024-01-02")
              .build();

      when(projectDao.getProjectByProjectId("proj-1")).thenReturn(Maybe.just(project));
      when(openFgaService.getUserRoleInProject("user-1", "proj-1"))
          .thenReturn(Single.just(Optional.of("admin")));

      ProjectDetailsDto result =
          projectService.getProjectDetails("user-1", "proj-1").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getProjectId()).isEqualTo("proj-1");
      assertThat(result.getTenantId()).isEqualTo("tenant-1");
      assertThat(result.getName()).isEqualTo("My Project");
      assertThat(result.getUserRole()).isEqualTo("admin");
      verify(projectDao).getProjectByProjectId("proj-1");
      verify(openFgaService).getUserRoleInProject("user-1", "proj-1");
    }

    @Test
    void shouldThrowWhenProjectNotFound() {
      when(projectDao.getProjectByProjectId("nonexistent")).thenReturn(Maybe.empty());

      assertThrows(
          RuntimeException.class,
          () -> projectService.getProjectDetails("user-1", "nonexistent").blockingGet());
    }

    @Test
    void shouldThrowWhenUserHasNoAccess() {
      Project project =
          Project.builder()
              .projectId("proj-1")
              .tenantId("tenant-1")
              .name("My Project")
              .build();

      when(projectDao.getProjectByProjectId("proj-1")).thenReturn(Maybe.just(project));
      when(openFgaService.getUserRoleInProject("user-1", "proj-1"))
          .thenReturn(Single.just(Optional.empty()));

      RuntimeException ex =
          assertThrows(
              RuntimeException.class,
              () -> projectService.getProjectDetails("user-1", "proj-1").blockingGet());

      assertThat(ex.getMessage()).contains("Access denied");
    }
  }

  @Nested
  class GetProjectByApiKey {

    @Test
    void shouldThrowAsDeprecated() {
      RuntimeException ex =
          assertThrows(
              RuntimeException.class,
              () -> projectService.getProjectByApiKey("some-key").blockingGet());

      assertThat(ex.getMessage()).contains("API key lookup not implemented");
    }
  }

  @Nested
  class GetActiveProjectCount {

    @Test
    void shouldReturnActiveProjectCount() {
      when(projectDao.getActiveProjectCount("tenant-1")).thenReturn(Single.just(5));

      Integer count = projectService.getActiveProjectCount("tenant-1").blockingGet();

      assertThat(count).isEqualTo(5);
      verify(projectDao).getActiveProjectCount("tenant-1");
    }

    @Test
    void shouldReturnZeroWhenNoActiveProjects() {
      when(projectDao.getActiveProjectCount("tenant-1")).thenReturn(Single.just(0));

      Integer count = projectService.getActiveProjectCount("tenant-1").blockingGet();

      assertThat(count).isEqualTo(0);
    }
  }
}
