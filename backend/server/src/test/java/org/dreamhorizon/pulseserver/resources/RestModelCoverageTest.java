package org.dreamhorizon.pulseserver.resources;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.dreamhorizon.pulseserver.dto.ProjectSummaryDto;
import org.dreamhorizon.pulseserver.model.LoginStatus;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthenticateResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.LoginRequest;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.LoginResponse;
import org.dreamhorizon.pulseserver.resources.v1.members.models.AddMemberRequest;
import org.dreamhorizon.pulseserver.resources.v1.members.models.MemberListResponse;
import org.dreamhorizon.pulseserver.resources.v1.members.models.MemberResponse;
import org.dreamhorizon.pulseserver.resources.v1.members.models.UpdateMemberRoleRequest;
import org.dreamhorizon.pulseserver.resources.v1.onboarding.models.OnboardingResponse;
import org.dreamhorizon.pulseserver.resources.v1.projects.clickhouse.models.AuditHistoryResponse;
import org.dreamhorizon.pulseserver.resources.v1.projects.clickhouse.models.AuditLogResponse;
import org.dreamhorizon.pulseserver.resources.v1.projects.clickhouse.models.CredentialsResponse;
import org.dreamhorizon.pulseserver.resources.v1.projects.models.CreateProjectRequest;
import org.dreamhorizon.pulseserver.resources.v1.projects.models.ProjectResponse;
import org.dreamhorizon.pulseserver.resources.v1.users.models.UserProjectsResponse;
import org.dreamhorizon.pulseserver.resources.notification.models.NotificationChannelDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SlackOAuthCallbackRequest;
import org.dreamhorizon.pulseserver.service.notification.models.SlackChannelConfig;
import org.dreamhorizon.pulseserver.resources.notification.models.SlackOAuthResponseDto;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for REST model/DTO classes to exercise Lombok-generated builders and getters.
 * Provides coverage for model classes that are typically instantiated via JSON deserialization.
 */
class RestModelCoverageTest {

  @Nested
  class OnboardingResponseTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      OnboardingResponse model = OnboardingResponse.builder()
          .userId("user-1")
          .email("user@test.com")
          .name("Test User")
          .tenantId("tenant-1")
          .tenantName("Test Tenant")
          .tier("free")
          .projectId("proj-1")
          .projectName("Test Project")
          .projectApiKey("api-key-123")
          .accessToken("access-token")
          .refreshToken("refresh-token")
          .tokenType("Bearer")
          .expiresIn(3600)
          .redirectTo("/dashboard")
          .build();

      assertThat(model.getUserId()).isEqualTo("user-1");
      assertThat(model.getEmail()).isEqualTo("user@test.com");
      assertThat(model.getName()).isEqualTo("Test User");
      assertThat(model.getTenantId()).isEqualTo("tenant-1");
      assertThat(model.getTenantName()).isEqualTo("Test Tenant");
      assertThat(model.getTier()).isEqualTo("free");
      assertThat(model.getProjectId()).isEqualTo("proj-1");
      assertThat(model.getProjectName()).isEqualTo("Test Project");
      assertThat(model.getProjectApiKey()).isEqualTo("api-key-123");
      assertThat(model.getAccessToken()).isEqualTo("access-token");
      assertThat(model.getRefreshToken()).isEqualTo("refresh-token");
      assertThat(model.getTokenType()).isEqualTo("Bearer");
      assertThat(model.getExpiresIn()).isEqualTo(3600);
      assertThat(model.getRedirectTo()).isEqualTo("/dashboard");
    }
  }

  @Nested
  class SlackOAuthCallbackRequestTest {

    @Test
    void shouldBuildAndAssertFields() {
      SlackOAuthCallbackRequest model = new SlackOAuthCallbackRequest();
      model.setCode("oauth-code");
      model.setProjectId("proj-1");
      model.setError(null);

      assertThat(model.getCode()).isEqualTo("oauth-code");
      assertThat(model.getProjectId()).isEqualTo("proj-1");
      assertThat(model.getError()).isNull();
      assertThat(model.hasError()).isFalse();
      assertThat(model.isValid()).isTrue();
      assertThat(model.getValidationError()).isNull();
    }

    @Test
    void shouldDetectError() {
      SlackOAuthCallbackRequest model = new SlackOAuthCallbackRequest();
      model.setError("access_denied");

      assertThat(model.hasError()).isTrue();
    }

    @Test
    void shouldReturnValidationErrorWhenCodeMissing() {
      SlackOAuthCallbackRequest model = new SlackOAuthCallbackRequest();
      model.setCode("");
      model.setProjectId("proj-1");

      assertThat(model.getValidationError()).isEqualTo("Authorization code is required");
    }
  }

  @Nested
  class CredentialsResponseTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      CredentialsResponse model = CredentialsResponse.builder()
          .projectId("proj-1")
          .clickhouseUsername("ch-user")
          .isActive(true)
          .createdAt("2024-01-01T00:00:00")
          .message("OK")
          .build();

      assertThat(model.getProjectId()).isEqualTo("proj-1");
      assertThat(model.getClickhouseUsername()).isEqualTo("ch-user");
      assertThat(model.getIsActive()).isTrue();
      assertThat(model.getCreatedAt()).isEqualTo("2024-01-01T00:00:00");
      assertThat(model.getMessage()).isEqualTo("OK");
    }
  }

  @Nested
  class AuditLogResponseTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      AuditLogResponse model = AuditLogResponse.builder()
          .id(1L)
          .projectId("proj-1")
          .action("CREATE")
          .performedBy("user@test.com")
          .details("Created credentials")
          .createdAt("2024-01-01T00:00:00")
          .build();

      assertThat(model.getId()).isEqualTo(1L);
      assertThat(model.getProjectId()).isEqualTo("proj-1");
      assertThat(model.getAction()).isEqualTo("CREATE");
      assertThat(model.getPerformedBy()).isEqualTo("user@test.com");
      assertThat(model.getDetails()).isEqualTo("Created credentials");
      assertThat(model.getCreatedAt()).isEqualTo("2024-01-01T00:00:00");
    }
  }

  @Nested
  class AuditHistoryResponseTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      AuditLogResponse log = AuditLogResponse.builder().id(1L).action("CREATE").build();
      AuditHistoryResponse model = AuditHistoryResponse.builder()
          .logs(List.of(log))
          .count(1)
          .build();

      assertThat(model.getLogs()).hasSize(1);
      assertThat(model.getLogs().get(0).getId()).isEqualTo(1L);
      assertThat(model.getCount()).isEqualTo(1);
    }
  }

  @Nested
  class LoginResponseTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      LoginResponse model = LoginResponse.builder()
          .status(LoginStatus.SUCCESS)
          .accessToken("token")
          .refreshToken("refresh")
          .userId("user-1")
          .email("user@test.com")
          .name("Test User")
          .tenantId("tenant-1")
          .tenantName("Tenant")
          .tenantRole("admin")
          .tier("free")
          .needsOnboarding(false)
          .tokenType("Bearer")
          .expiresIn(3600)
          .build();

      assertThat(model.getStatus()).isEqualTo(LoginStatus.SUCCESS);
      assertThat(model.getAccessToken()).isEqualTo("token");
      assertThat(model.getRefreshToken()).isEqualTo("refresh");
      assertThat(model.getUserId()).isEqualTo("user-1");
      assertThat(model.getEmail()).isEqualTo("user@test.com");
      assertThat(model.getName()).isEqualTo("Test User");
      assertThat(model.getTenantId()).isEqualTo("tenant-1");
      assertThat(model.getTenantName()).isEqualTo("Tenant");
      assertThat(model.getTenantRole()).isEqualTo("admin");
      assertThat(model.getTier()).isEqualTo("free");
      assertThat(model.getNeedsOnboarding()).isFalse();
      assertThat(model.getTokenType()).isEqualTo("Bearer");
      assertThat(model.getExpiresIn()).isEqualTo(3600);
    }
  }

  @Nested
  class LoginRequestTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      LoginRequest model = LoginRequest.builder()
          .firebaseIdToken("firebase-token-123")
          .build();

      assertThat(model.getFirebaseIdToken()).isEqualTo("firebase-token-123");
    }
  }

  @Nested
  class AuthenticateResponseDtoTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      ProjectSummaryDto project = ProjectSummaryDto.builder()
          .projectId("proj-1")
          .name("Project")
          .description("Desc")
          .role("admin")
          .isActive(true)
          .createdAt("2024-01-01")
          .build();

      AuthenticateResponseDto model = AuthenticateResponseDto.builder()
          .accessToken("token")
          .expiresIn(3600)
          .idToken("id-token")
          .refreshToken("refresh")
          .tokenType("Bearer")
          .userId("user-1")
          .tenantId("tenant-1")
          .needsOnboarding(false)
          .projects(List.of(project))
          .build();

      assertThat(model.getAccessToken()).isEqualTo("token");
      assertThat(model.getExpiresIn()).isEqualTo(3600);
      assertThat(model.getIdToken()).isEqualTo("id-token");
      assertThat(model.getRefreshToken()).isEqualTo("refresh");
      assertThat(model.getTokenType()).isEqualTo("Bearer");
      assertThat(model.getUserId()).isEqualTo("user-1");
      assertThat(model.getTenantId()).isEqualTo("tenant-1");
      assertThat(model.getNeedsOnboarding()).isFalse();
      assertThat(model.getProjects()).hasSize(1);
      assertThat(model.getProjects().get(0).getProjectId()).isEqualTo("proj-1");
    }
  }

  @Nested
  class MemberResponseTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      MemberResponse model = MemberResponse.builder()
          .userId("user-1")
          .email("user@test.com")
          .name("Test User")
          .role("admin")
          .status("active")
          .lastLoginAt("2024-01-01T00:00:00")
          .build();

      assertThat(model.getUserId()).isEqualTo("user-1");
      assertThat(model.getEmail()).isEqualTo("user@test.com");
      assertThat(model.getName()).isEqualTo("Test User");
      assertThat(model.getRole()).isEqualTo("admin");
      assertThat(model.getStatus()).isEqualTo("active");
      assertThat(model.getLastLoginAt()).isEqualTo("2024-01-01T00:00:00");
    }
  }

  @Nested
  class MemberListResponseTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      MemberResponse member = MemberResponse.builder().userId("user-1").build();
      MemberListResponse model = MemberListResponse.builder()
          .members(List.of(member))
          .totalCount(1)
          .build();

      assertThat(model.getMembers()).hasSize(1);
      assertThat(model.getTotalCount()).isEqualTo(1);
    }
  }

  @Nested
  class AddMemberRequestTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      AddMemberRequest model = AddMemberRequest.builder()
          .email("user@test.com")
          .role("member")
          .build();

      assertThat(model.getEmail()).isEqualTo("user@test.com");
      assertThat(model.getRole()).isEqualTo("member");
    }
  }

  @Nested
  class UpdateMemberRoleRequestTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      UpdateMemberRoleRequest model = UpdateMemberRoleRequest.builder()
          .newRole("admin")
          .build();

      assertThat(model.getNewRole()).isEqualTo("admin");
    }
  }

  @Nested
  class UserProjectsResponseTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      UserProjectsResponse.ProjectSummary summary = UserProjectsResponse.ProjectSummary.builder()
          .projectId("proj-1")
          .name("Project")
          .description("Desc")
          .isActive(true)
          .role("admin")
          .build();

      UserProjectsResponse model = UserProjectsResponse.builder()
          .tenantId("tenant-1")
          .tenantName("Tenant")
          .projects(List.of(summary))
          .redirectTo("/dashboard")
          .build();

      assertThat(model.getTenantId()).isEqualTo("tenant-1");
      assertThat(model.getTenantName()).isEqualTo("Tenant");
      assertThat(model.getProjects()).hasSize(1);
      assertThat(model.getProjects().get(0).getProjectId()).isEqualTo("proj-1");
      assertThat(model.getRedirectTo()).isEqualTo("/dashboard");
    }
  }

  @Nested
  class CreateProjectRequestTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      CreateProjectRequest model = CreateProjectRequest.builder()
          .name("My Project")
          .description("Project description")
          .build();

      assertThat(model.getName()).isEqualTo("My Project");
      assertThat(model.getDescription()).isEqualTo("Project description");
    }
  }

  @Nested
  class ProjectResponseTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      ProjectResponse model = ProjectResponse.builder()
          .projectId("proj-1")
          .name("Project")
          .description("Desc")
          .tenantId("tenant-1")
          .apiKey("api-key")
          .createdAt("2024-01-01")
          .createdBy("user@test.com")
          .build();

      assertThat(model.getProjectId()).isEqualTo("proj-1");
      assertThat(model.getName()).isEqualTo("Project");
      assertThat(model.getDescription()).isEqualTo("Desc");
      assertThat(model.getTenantId()).isEqualTo("tenant-1");
      assertThat(model.getApiKey()).isEqualTo("api-key");
      assertThat(model.getCreatedAt()).isEqualTo("2024-01-01");
      assertThat(model.getCreatedBy()).isEqualTo("user@test.com");
    }
  }

  @Nested
  class SlackOAuthResponseDtoTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      SlackOAuthResponseDto model = SlackOAuthResponseDto.builder()
          .success(true)
          .workspaceId("ws-1")
          .workspaceName("Workspace")
          .channelId(123L)
          .message("OK")
          .installUrl("https://slack.com/install")
          .build();

      assertThat(model.getSuccess()).isTrue();
      assertThat(model.getWorkspaceId()).isEqualTo("ws-1");
      assertThat(model.getWorkspaceName()).isEqualTo("Workspace");
      assertThat(model.getChannelId()).isEqualTo(123L);
      assertThat(model.getMessage()).isEqualTo("OK");
      assertThat(model.getInstallUrl()).isEqualTo("https://slack.com/install");
    }
  }

  @Nested
  class NotificationChannelDtoTest {

    @Test
    void shouldBuildAndAssertAllFields() {
      SlackChannelConfig config = SlackChannelConfig.builder()
          .accessToken("xoxb-token")
          .workspaceId("T123")
          .botName("Pulse Bot")
          .build();
      NotificationChannelDto model = NotificationChannelDto.builder()
          .id(1L)
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .name("Slack Channel")
          .config(config)
          .isActive(true)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      assertThat(model.getId()).isEqualTo(1L);
      assertThat(model.getProjectId()).isEqualTo("proj-1");
      assertThat(model.getChannelType()).isEqualTo(ChannelType.SLACK);
      assertThat(model.getName()).isEqualTo("Slack Channel");
      assertThat(model.getConfig()).isEqualTo(config);
      assertThat(model.getIsActive()).isTrue();
      assertThat(model.getCreatedAt()).isNotNull();
      assertThat(model.getUpdatedAt()).isNotNull();
    }
  }
}
