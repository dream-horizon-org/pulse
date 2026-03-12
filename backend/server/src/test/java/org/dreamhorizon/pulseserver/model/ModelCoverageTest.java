package org.dreamhorizon.pulseserver.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.dto.ProjectCreationResult;
import org.dreamhorizon.pulseserver.dto.ProjectDetailsDto;
import org.dreamhorizon.pulseserver.dto.ProjectSummaryDto;
import org.dreamhorizon.pulseserver.dto.UserProfileDto;
import org.dreamhorizon.pulseserver.dto.request.CreateProjectRequest;
import org.dreamhorizon.pulseserver.dto.request.CreateTenantRequest;
import org.dreamhorizon.pulseserver.dto.request.ReqUserInfo;
import org.dreamhorizon.pulseserver.dto.request.UpdateProjectRequest;
import org.dreamhorizon.pulseserver.service.ProjectAuditAction;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.EmailSuppression;
import org.dreamhorizon.pulseserver.service.notification.models.EmailTemplateBody;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationChannel;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationLog;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationMessage;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationResult;
import org.dreamhorizon.pulseserver.service.notification.models.SlackChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.TemplateBody;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate;
import org.dreamhorizon.pulseserver.service.notification.models.QueuedNotification;
import org.dreamhorizon.pulseserver.service.notification.models.SuppressionReason;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ModelCoverageTest {

  @Nested
  class ProjectRoleEnum {

    @Test
    void shouldHaveValues() {
      assertThat(ProjectRole.values()).containsExactly(ProjectRole.ADMIN, ProjectRole.EDITOR, ProjectRole.VIEWER);
    }

    @Test
    void shouldValueOf() {
      assertThat(ProjectRole.valueOf("ADMIN")).isEqualTo(ProjectRole.ADMIN);
      assertThat(ProjectRole.valueOf("EDITOR")).isEqualTo(ProjectRole.EDITOR);
      assertThat(ProjectRole.valueOf("VIEWER")).isEqualTo(ProjectRole.VIEWER);
    }

    @Test
    void shouldFromValue() {
      assertThat(ProjectRole.fromValue("admin")).isEqualTo(ProjectRole.ADMIN);
      assertThat(ProjectRole.fromValue("ADMIN")).isEqualTo(ProjectRole.ADMIN);
      assertThat(ProjectRole.fromValue("editor")).isEqualTo(ProjectRole.EDITOR);
      assertThat(ProjectRole.fromValue("viewer")).isEqualTo(ProjectRole.VIEWER);
    }

    @Test
    void shouldRejectInvalidFromValue() {
      assertThatThrownBy(() -> ProjectRole.fromValue(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot be null");
      assertThatThrownBy(() -> ProjectRole.fromValue("invalid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid project role");
    }

    @Test
    void shouldGetValue() {
      assertThat(ProjectRole.ADMIN.getValue()).isEqualTo("admin");
      assertThat(ProjectRole.EDITOR.getValue()).isEqualTo("editor");
      assertThat(ProjectRole.VIEWER.getValue()).isEqualTo("viewer");
    }

    @Test
    void shouldCanEdit() {
      assertThat(ProjectRole.ADMIN.canEdit()).isTrue();
      assertThat(ProjectRole.EDITOR.canEdit()).isTrue();
      assertThat(ProjectRole.VIEWER.canEdit()).isFalse();
    }

    @Test
    void shouldIsAdmin() {
      assertThat(ProjectRole.ADMIN.isAdmin()).isTrue();
      assertThat(ProjectRole.EDITOR.isAdmin()).isFalse();
      assertThat(ProjectRole.VIEWER.isAdmin()).isFalse();
    }

    @Test
    void shouldToString() {
      assertThat(ProjectRole.ADMIN.toString()).isEqualTo("admin");
    }
  }

  @Nested
  class TenantRoleEnum {

    @Test
    void shouldHaveValues() {
      assertThat(TenantRole.values()).containsExactly(TenantRole.OWNER, TenantRole.ADMIN, TenantRole.MEMBER);
    }

    @Test
    void shouldValueOf() {
      assertThat(TenantRole.valueOf("OWNER")).isEqualTo(TenantRole.OWNER);
    }

    @Test
    void shouldFromValue() {
      assertThat(TenantRole.fromValue("owner")).isEqualTo(TenantRole.OWNER);
      assertThat(TenantRole.fromValue("admin")).isEqualTo(TenantRole.ADMIN);
      assertThat(TenantRole.fromValue("member")).isEqualTo(TenantRole.MEMBER);
    }

    @Test
    void shouldRejectInvalidFromValue() {
      assertThatThrownBy(() -> TenantRole.fromValue(null))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> TenantRole.fromValue("invalid"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldIsAdmin() {
      assertThat(TenantRole.OWNER.isAdmin()).isTrue();
      assertThat(TenantRole.ADMIN.isAdmin()).isTrue();
      assertThat(TenantRole.MEMBER.isAdmin()).isFalse();
    }
  }

  @Nested
  class LoginStatusEnum {

    @Test
    void shouldHaveValues() {
      assertThat(LoginStatus.values()).contains(
          LoginStatus.SUCCESS, LoginStatus.NEEDS_ONBOARDING,
          LoginStatus.REQUIRES_VERIFICATION, LoginStatus.PENDING_ACTIVATION);
    }

    @Test
    void shouldFromValue() {
      assertThat(LoginStatus.fromValue("success")).isEqualTo(LoginStatus.SUCCESS);
      assertThat(LoginStatus.fromValue("needs_onboarding")).isEqualTo(LoginStatus.NEEDS_ONBOARDING);
    }

    @Test
    void shouldRejectInvalidFromValue() {
      assertThatThrownBy(() -> LoginStatus.fromValue(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldIsAuthenticated() {
      assertThat(LoginStatus.SUCCESS.isAuthenticated()).isTrue();
      assertThat(LoginStatus.NEEDS_ONBOARDING.isAuthenticated()).isFalse();
    }

    @Test
    void shouldRequiresAction() {
      assertThat(LoginStatus.SUCCESS.requiresAction()).isFalse();
      assertThat(LoginStatus.NEEDS_ONBOARDING.requiresAction()).isTrue();
    }
  }

  @Nested
  class UserStatusEnum {

    @Test
    void shouldHaveValues() {
      assertThat(UserStatus.values()).contains(
          UserStatus.PENDING, UserStatus.ACTIVE, UserStatus.SUSPENDED, UserStatus.DELETED);
    }

    @Test
    void shouldFromValue() {
      assertThat(UserStatus.fromValue("active")).isEqualTo(UserStatus.ACTIVE);
      assertThat(UserStatus.fromValue("pending")).isEqualTo(UserStatus.PENDING);
    }

    @Test
    void shouldRejectInvalidFromValue() {
      assertThatThrownBy(() -> UserStatus.fromValue(null)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class ProjectAuditActionEnum {

    @Test
    void shouldHaveValues() {
      assertThat(ProjectAuditAction.values()).contains(
          ProjectAuditAction.CREDENTIALS_SETUP,
          ProjectAuditAction.CREDENTIALS_UPDATED,
          ProjectAuditAction.CREDENTIALS_REMOVED,
          ProjectAuditAction.CREDENTIALS_ROTATED);
    }

    @Test
    void shouldGetValue() {
      assertThat(ProjectAuditAction.CREDENTIALS_SETUP.getValue()).isEqualTo("CREDENTIALS_SETUP");
    }
  }

  @Nested
  class ProjectModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Project project = Project.builder()
          .id(1L)
          .projectId("proj-123")
          .tenantId("tenant-1")
          .name("My Project")
          .description("Description")
          .apiKey("pulse_proj123_sk_xxx")
          .isActive(true)
          .createdBy("user-1")
          .createdAt("2024-01-01T00:00:00")
          .updatedAt("2024-01-02T00:00:00")
          .build();

      assertThat(project.getId()).isEqualTo(1L);
      assertThat(project.getProjectId()).isEqualTo("proj-123");
      assertThat(project.getTenantId()).isEqualTo("tenant-1");
      assertThat(project.getName()).isEqualTo("My Project");
      assertThat(project.getDescription()).isEqualTo("Description");
      assertThat(project.getApiKey()).isEqualTo("pulse_proj123_sk_xxx");
      assertThat(project.getIsActive()).isTrue();
      assertThat(project.getCreatedBy()).isEqualTo("user-1");
      assertThat(project.getCreatedAt()).isEqualTo("2024-01-01T00:00:00");
      assertThat(project.getUpdatedAt()).isEqualTo("2024-01-02T00:00:00");
      assertThat(project.toString()).isNotNull();
    }
  }

  @Nested
  class UserModel {

    @Test
    void shouldBuildAndReadAllFields() {
      User user = User.builder()
          .id(1L)
          .userId("user-123")
          .email("user@example.com")
          .name("Test User")
          .status("active")
          .firebaseUid("firebase-uid")
          .lastLoginAt("2024-01-01T00:00:00")
          .isActive(true)
          .createdAt("2024-01-01")
          .updatedAt("2024-01-02")
          .build();

      assertThat(user.getId()).isEqualTo(1L);
      assertThat(user.getUserId()).isEqualTo("user-123");
      assertThat(user.getEmail()).isEqualTo("user@example.com");
      assertThat(user.getName()).isEqualTo("Test User");
      assertThat(user.getStatus()).isEqualTo("active");
      assertThat(user.getFirebaseUid()).isEqualTo("firebase-uid");
      assertThat(user.getLastLoginAt()).isEqualTo("2024-01-01T00:00:00");
      assertThat(user.getIsActive()).isTrue();
      assertThat(user.getCreatedAt()).isEqualTo("2024-01-01");
      assertThat(user.getUpdatedAt()).isEqualTo("2024-01-02");
      assertThat(user.toString()).isNotNull();
    }

    @Test
    void shouldToBuilder() {
      User user = User.builder().userId("u1").email("e@e.com").build();
      User copied = user.toBuilder().name("New Name").build();
      assertThat(copied.getUserId()).isEqualTo("u1");
      assertThat(copied.getName()).isEqualTo("New Name");
    }
  }

  @Nested
  class ClickhouseProjectCredentialsModel {

    @Test
    void shouldBuildAndReadAllFields() {
      ClickhouseProjectCredentials creds = ClickhouseProjectCredentials.builder()
          .id(1L)
          .projectId("proj-1")
          .clickhouseUsername("project_proj1")
          .clickhousePasswordEncrypted("enc")
          .encryptionSalt("salt")
          .passwordDigest("digest")
          .isActive(true)
          .createdAt("2024-01-01")
          .updatedAt("2024-01-02")
          .build();

      assertThat(creds.getId()).isEqualTo(1L);
      assertThat(creds.getProjectId()).isEqualTo("proj-1");
      assertThat(creds.getClickhouseUsername()).isEqualTo("project_proj1");
      assertThat(creds.getClickhousePasswordEncrypted()).isEqualTo("enc");
      assertThat(creds.getEncryptionSalt()).isEqualTo("salt");
      assertThat(creds.getPasswordDigest()).isEqualTo("digest");
      assertThat(creds.getIsActive()).isTrue();
      assertThat(creds.getCreatedAt()).isEqualTo("2024-01-01");
      assertThat(creds.getUpdatedAt()).isEqualTo("2024-01-02");
    }
  }

  @Nested
  class QueryConfigurationBuilder {

    @Test
    void shouldBuildWithAllFields() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .timeoutMs(5000)
          .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
          .tenantId("tenant-1")
          .projectId("proj-1")
          .build();

      assertThat(config.getQuery()).isEqualTo("SELECT 1");
      assertThat(config.getTimeoutMs()).isEqualTo(5000);
      assertThat(config.getJobCreationMode()).isEqualTo(JobCreationMode.JOB_CREATION_OPTIONAL);
      assertThat(config.getTenantId()).isEqualTo("tenant-1");
      assertThat(config.getProjectId()).isEqualTo("proj-1");
      assertThat(config.isUseLegacySql()).isFalse();
    }

    @Test
    void shouldUseDefaultTimeout() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1").build();
      assertThat(config.getTimeoutMs()).isEqualTo(60000);
    }
  }

  @Nested
  class ProjectCreationResultTests {

    @Test
    void shouldBuildAndReadAllFields() {
      org.dreamhorizon.pulseserver.dao.project.models.Project daoProject =
          org.dreamhorizon.pulseserver.dao.project.models.Project.builder()
              .projectId("proj-1")
              .name("Test")
              .build();
      org.dreamhorizon.pulseserver.dto.ProjectCreationResult result = org.dreamhorizon.pulseserver.dto.ProjectCreationResult.builder()
          .project(daoProject)
          .rawApiKey("raw-key-123")
          .build();

      assertThat(result.getProject()).isEqualTo(daoProject);
      assertThat(result.getRawApiKey()).isEqualTo("raw-key-123");
    }
  }

  @Nested
  class ProjectDetailsDtoTests {

    @Test
    void shouldBuildAndReadAllFields() {
      org.dreamhorizon.pulseserver.dto.ProjectDetailsDto dto = org.dreamhorizon.pulseserver.dto.ProjectDetailsDto.builder()
          .projectId("proj-1")
          .tenantId("tenant-1")
          .name("Project")
          .description("Desc")
          .apiKey("key")
          .isActive(true)
          .createdBy("user-1")
          .createdAt("2024-01-01")
          .updatedAt("2024-01-02")
          .userRole("admin")
          .build();

      assertThat(dto.getProjectId()).isEqualTo("proj-1");
      assertThat(dto.getTenantId()).isEqualTo("tenant-1");
      assertThat(dto.getName()).isEqualTo("Project");
      assertThat(dto.getDescription()).isEqualTo("Desc");
      assertThat(dto.getApiKey()).isEqualTo("key");
      assertThat(dto.getIsActive()).isTrue();
      assertThat(dto.getCreatedBy()).isEqualTo("user-1");
      assertThat(dto.getCreatedAt()).isEqualTo("2024-01-01");
      assertThat(dto.getUpdatedAt()).isEqualTo("2024-01-02");
      assertThat(dto.getUserRole()).isEqualTo("admin");
    }
  }

  @Nested
  class ProjectSummaryDtoTests {

    @Test
    void shouldBuildAndReadAllFields() {
      org.dreamhorizon.pulseserver.dto.ProjectSummaryDto dto = org.dreamhorizon.pulseserver.dto.ProjectSummaryDto.builder()
          .projectId("proj-1")
          .name("Project")
          .description("Desc")
          .role("admin")
          .isActive(true)
          .createdAt("2024-01-01")
          .build();

      assertThat(dto.getProjectId()).isEqualTo("proj-1");
      assertThat(dto.getName()).isEqualTo("Project");
      assertThat(dto.getDescription()).isEqualTo("Desc");
      assertThat(dto.getRole()).isEqualTo("admin");
      assertThat(dto.getIsActive()).isTrue();
      assertThat(dto.getCreatedAt()).isEqualTo("2024-01-01");
    }
  }

  @Nested
  class UserProfileDtoTests {

    @Test
    void shouldBuildAndReadAllFields() {
      org.dreamhorizon.pulseserver.dto.UserProfileDto dto = org.dreamhorizon.pulseserver.dto.UserProfileDto.builder()
          .userId("user-1")
          .email("u@e.com")
          .name("User")
          .tenantRole("admin")
          .isActive(true)
          .build();

      assertThat(dto.getUserId()).isEqualTo("user-1");
      assertThat(dto.getEmail()).isEqualTo("u@e.com");
      assertThat(dto.getName()).isEqualTo("User");
      assertThat(dto.getTenantRole()).isEqualTo("admin");
      assertThat(dto.getIsActive()).isTrue();
    }
  }

  @Nested
  class CreateProjectRequestTests {

    @Test
    void shouldBuildAndReadAllFields() {
      org.dreamhorizon.pulseserver.dto.request.CreateProjectRequest req = org.dreamhorizon.pulseserver.dto.request.CreateProjectRequest.builder()
          .name("My Project")
          .description("Description")
          .build();

      assertThat(req.getName()).isEqualTo("My Project");
      assertThat(req.getDescription()).isEqualTo("Description");
    }
  }

  @Nested
  class CreateTenantRequestTests {

    @Test
    void shouldBuildAndReadAllFields() {
      org.dreamhorizon.pulseserver.dto.request.CreateTenantRequest req = org.dreamhorizon.pulseserver.dto.request.CreateTenantRequest.builder()
          .name("Tenant")
          .description("Desc")
          .build();

      assertThat(req.getName()).isEqualTo("Tenant");
      assertThat(req.getDescription()).isEqualTo("Desc");
    }
  }

  @Nested
  class UpdateProjectRequestTests {

    @Test
    void shouldBuildAndReadAllFields() {
      org.dreamhorizon.pulseserver.dto.request.UpdateProjectRequest req = org.dreamhorizon.pulseserver.dto.request.UpdateProjectRequest.builder()
          .name("Updated")
          .description("New desc")
          .isActive(false)
          .build();

      assertThat(req.getName()).isEqualTo("Updated");
      assertThat(req.getDescription()).isEqualTo("New desc");
      assertThat(req.getIsActive()).isFalse();
    }
  }

  @Nested
  class ReqUserInfoTests {

    @Test
    void shouldBuildAndReadAllFields() {
      org.dreamhorizon.pulseserver.dto.request.ReqUserInfo req = org.dreamhorizon.pulseserver.dto.request.ReqUserInfo.builder()
          .userId("user-1")
          .email("u@e.com")
          .name("User")
          .build();

      assertThat(req.getUserId()).isEqualTo("user-1");
      assertThat(req.getEmail()).isEqualTo("u@e.com");
      assertThat(req.getName()).isEqualTo("User");
    }
  }

  @Nested
  class NotificationModels {

    @Test
    void notificationMessageBuilder() {
      ChannelConfig channelConfig = SlackChannelConfig.builder().accessToken("token").build();
      TemplateBody templateBody = EmailTemplateBody.builder().subject("Subj").html("body").build();
      NotificationMessage msg = NotificationMessage.builder()
          .logId(1L)
          .projectId("proj-1")
          .idempotencyKey("key")
          .channelType(ChannelType.EMAIL)
          .channelId(2L)
          .channelConfig(channelConfig)
          .templateId(3L)
          .templateBody(templateBody)
          .recipient("a@b.com")
          .subject("Subj")
          .params(Map.of("k", "v"))
          .metadata(Map.of())
          .build();

      assertThat(msg.getLogId()).isEqualTo(1L);
      assertThat(msg.getProjectId()).isEqualTo("proj-1");
      assertThat(msg.getIdempotencyKey()).isEqualTo("key");
      assertThat(msg.getChannelType()).isEqualTo(ChannelType.EMAIL);
      assertThat(msg.getChannelId()).isEqualTo(2L);
      assertThat(msg.getChannelConfig()).isEqualTo(channelConfig);
      assertThat(msg.getTemplateId()).isEqualTo(3L);
      assertThat(msg.getTemplateBody()).isEqualTo(templateBody);
      assertThat(msg.getRecipient()).isEqualTo("a@b.com");
      assertThat(msg.getSubject()).isEqualTo("Subj");
      assertThat(msg.getParams()).containsEntry("k", "v");
      assertThat(msg.getMetadata()).isEmpty();
    }

    @Test
    void notificationResultBuilder() {
      NotificationResult result = NotificationResult.builder()
          .success(true)
          .externalId("ext-1")
          .errorCode("ERR")
          .errorMessage("msg")
          .permanentFailure(false)
          .providerResponse("resp")
          .latencyMs(100L)
          .build();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getExternalId()).isEqualTo("ext-1");
      assertThat(result.getErrorCode()).isEqualTo("ERR");
      assertThat(result.getErrorMessage()).isEqualTo("msg");
      assertThat(result.isPermanentFailure()).isFalse();
      assertThat(result.getProviderResponse()).isEqualTo("resp");
      assertThat(result.getLatencyMs()).isEqualTo(100L);
    }

    @Test
    void queuedNotificationBuilder() {
      NotificationMessage msg = NotificationMessage.builder().recipient("a@b.com").build();
      QueuedNotification q = QueuedNotification.builder()
          .message(msg)
          .receiptHandle("handle")
          .messageId("msg-id")
          .receiveCount(2)
          .build();

      assertThat(q.getMessage()).isEqualTo(msg);
      assertThat(q.getReceiptHandle()).isEqualTo("handle");
      assertThat(q.getMessageId()).isEqualTo("msg-id");
      assertThat(q.getReceiveCount()).isEqualTo(2);
    }

    @Test
    void notificationLogBuilder() {
      NotificationLog log = NotificationLog.builder()
          .id(1L)
          .projectId("proj-1")
          .idempotencyKey("key")
          .channelType(ChannelType.SLACK)
          .channelId(2L)
          .templateId(3L)
          .recipient("r")
          .subject("s")
          .status(NotificationStatus.SENT)
          .attemptCount(1)
          .lastAttemptAt(Instant.EPOCH)
          .errorMessage("err")
          .errorCode("E1")
          .externalId("ext")
          .providerResponse("pr")
          .latencyMs(50)
          .createdAt(Instant.EPOCH)
          .sentAt(Instant.EPOCH)
          .build();

      assertThat(log.getId()).isEqualTo(1L);
      assertThat(log.getProjectId()).isEqualTo("proj-1");
      assertThat(log.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void emailSuppressionBuilder() {
      EmailSuppression s = EmailSuppression.builder()
          .id(1L)
          .projectId("proj-1")
          .email("a@b.com")
          .reason(SuppressionReason.BOUNCE)
          .bounceType("type")
          .sourceMessageId("msg")
          .suppressedAt(Instant.EPOCH)
          .expiresAt(Instant.EPOCH)
          .build();

      assertThat(s.getId()).isEqualTo(1L);
      assertThat(s.getProjectId()).isEqualTo("proj-1");
      assertThat(s.getEmail()).isEqualTo("a@b.com");
      assertThat(s.getReason()).isEqualTo(SuppressionReason.BOUNCE);
    }

    @Test
    void notificationTemplateBuilder() {
      TemplateBody body = EmailTemplateBody.builder().subject("Alert").html("body").build();
      NotificationTemplate t = NotificationTemplate.builder()
          .id(1L)
          .eventName("alert")
          .channelType(ChannelType.EMAIL)
          .version(1)
          .body(body)
          .isActive(true)
          .createdAt(Instant.EPOCH)
          .updatedAt(Instant.EPOCH)
          .build();

      assertThat(t.getId()).isEqualTo(1L);
      assertThat(t.getEventName()).isEqualTo("alert");
      assertThat(t.getBody()).isEqualTo(body);
    }

    @Test
    void notificationChannelBuilder() {
      ChannelConfig config = SlackChannelConfig.builder().accessToken("token").build();
      NotificationChannel ch = NotificationChannel.builder()
          .id(1L)
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .name("Channel")
          .config(config)
          .isActive(true)
          .createdAt(Instant.EPOCH)
          .updatedAt(Instant.EPOCH)
          .build();

      assertThat(ch.getId()).isEqualTo(1L);
      assertThat(ch.getProjectId()).isEqualTo("proj-1");
      assertThat(ch.getChannelType()).isEqualTo(ChannelType.SLACK);
      assertThat(ch.getName()).isEqualTo("Channel");
      assertThat(ch.getConfig()).isEqualTo(config);
    }
  }

  @Nested
  class ChannelTypeEnum {

    @Test
    void shouldHaveValues() {
      assertThat(ChannelType.values()).containsExactly(ChannelType.SLACK, ChannelType.SLACK_WEBHOOK, ChannelType.EMAIL, ChannelType.TEAMS, ChannelType.ALL);
    }

    @Test
    void shouldValueOf() {
      assertThat(ChannelType.valueOf("EMAIL")).isEqualTo(ChannelType.EMAIL);
    }
  }

  @Nested
  class NotificationStatusEnum {

    @Test
    void shouldHaveValues() {
      assertThat(NotificationStatus.values()).contains(
          NotificationStatus.PENDING, NotificationStatus.SENT, NotificationStatus.FAILED);
    }
  }

  @Nested
  class SuppressionReasonEnum {

    @Test
    void shouldHaveValues() {
      assertThat(SuppressionReason.values()).containsExactly(
          SuppressionReason.BOUNCE, SuppressionReason.COMPLAINT, SuppressionReason.MANUAL);
    }
  }
}
