package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.resources.notification.models.NotificationBatchResponseDto;
import org.dreamhorizon.pulseserver.service.TenantMemberService;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
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
class ProjectMemberServiceTest {

  @Mock
  UserService userService;

  @Mock
  ProjectDao projectDao;

  @Mock
  OpenFgaService openFgaService;

  @Mock
  EmailService emailService;

  @Mock
  TenantMemberService tenantMemberService;

  @Mock
  NotificationService notificationService;

  ProjectMemberService projectMemberService;

  private static final String PROJECT_ID = "proj-1";
  private static final String TENANT_ID = "tenant-1";
  private static final String ADMIN_ID = "admin-1";
  private static final String USER_ID = "user-1";

  @BeforeEach
  void setUp() {
    projectMemberService = new ProjectMemberService(
        userService, projectDao, openFgaService, emailService,
        tenantMemberService, notificationService);
    // Stub notification service for fire-and-forget calls in success paths
    when(notificationService.sendNotification(anyString(), any()))
        .thenReturn(Single.just(NotificationBatchResponseDto.builder().idempotencyKey("batch-1").build()));
  }

  private Project createProject(String projectId, String tenantId, String name) {
    return Project.builder()
        .projectId(projectId)
        .tenantId(tenantId)
        .name(name)
        .isActive(true)
        .build();
  }

  private User createUser(String userId, String email, String name) {
    return User.builder()
        .userId(userId)
        .email(email)
        .name(name)
        .build();
  }

  @Nested
  class AddMemberToProject {

    @Test
    void shouldRejectInvalidRole() {
      Exception ex = assertThrows(IllegalArgumentException.class, () ->
          projectMemberService.addMemberToProject(PROJECT_ID, "user@test.com", "invalid-role", ADMIN_ID)
              .blockingGet());

      assertThat(ex.getMessage()).contains("Invalid project role");
      assertThat(ex.getMessage()).contains("admin, editor, viewer");
      verify(openFgaService, never()).assignProjectRole(any(), any(), any());
    }

    @Test
    void shouldFailWhenProjectNotFound() {
      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.empty());
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(createUser(ADMIN_ID, "a@t.com", "Admin")));

      Exception ex = assertThrows(RuntimeException.class, () ->
          projectMemberService.addMemberToProject(PROJECT_ID, "user@test.com", "viewer", ADMIN_ID)
              .blockingGet());

      assertThat(ex.getMessage()).contains("Project not found");
    }

    @Test
    void shouldFailWhenAddedByIsNotAdmin() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@test.com", "Admin");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(false));

      Exception ex = assertThrows(IllegalArgumentException.class, () ->
          projectMemberService.addMemberToProject(PROJECT_ID, "newuser@test.com", "viewer", ADMIN_ID)
              .blockingGet());

      assertThat(ex.getMessage()).contains("Only project admins can add members");
    }

    @Test
    void shouldAddMemberSuccessfully() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@test.com", "Admin User");
      User newUser = createUser(USER_ID, "newuser@test.com", "New User");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(userService.getOrCreateUser("newuser@test.com", "newuser@test.com"))
          .thenReturn(Single.just(newUser));
      when(openFgaService.getUserTenantRole(USER_ID, TENANT_ID))
          .thenReturn(Single.just(Optional.of("member")));
      when(openFgaService.assignProjectRole(USER_ID, PROJECT_ID, "viewer"))
          .thenReturn(Completable.complete());

      User result = projectMemberService.addMemberToProject(
          PROJECT_ID, "newuser@test.com", "viewer", ADMIN_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo(USER_ID);
      assertThat(result.getEmail()).isEqualTo("newuser@test.com");
      verify(openFgaService).assignProjectRole(USER_ID, PROJECT_ID, "viewer");
      verify(notificationService).sendNotification(anyString(), any());
    }

    @Test
    void shouldFailWhenAdminUserNotFound() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.error(new RuntimeException("User not found")));

      Exception ex = assertThrows(RuntimeException.class, () ->
          projectMemberService.addMemberToProject(PROJECT_ID, "newuser@test.com", "viewer", ADMIN_ID)
              .blockingGet());

      assertThat(ex.getMessage()).contains("Admin user not found");
      verify(openFgaService, never()).assignProjectRole(any(), any(), any());
    }

    @Test
    void shouldAddMemberAndAutoAddToTenantWhenNotInTenant() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@test.com", "Admin User");
      User newUser = createUser(USER_ID, "newuser@test.com", "New User");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(userService.getOrCreateUser("newuser@test.com", "newuser@test.com"))
          .thenReturn(Single.just(newUser));
      when(openFgaService.getUserTenantRole(USER_ID, TENANT_ID))
          .thenReturn(Single.just(Optional.empty()));
      when(tenantMemberService.addUserToTenant(TENANT_ID, "newuser@test.com", "member", ADMIN_ID))
          .thenReturn(Single.just(newUser));
      when(openFgaService.assignProjectRole(USER_ID, PROJECT_ID, "editor"))
          .thenReturn(Completable.complete());

      User result = projectMemberService.addMemberToProject(
          PROJECT_ID, "newuser@test.com", "editor", ADMIN_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo(USER_ID);
      verify(tenantMemberService).addUserToTenant(TENANT_ID, "newuser@test.com", "member", ADMIN_ID);
      verify(openFgaService).assignProjectRole(USER_ID, PROJECT_ID, "editor");
    }
  }

  @Nested
  class RemoveMemberFromProject {

    @Test
    void shouldFailWhenProjectNotFound() {
      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.empty());
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(createUser(ADMIN_ID, "a@t.com", "Admin")));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(createUser(USER_ID, "u@t.com", "User")));

      Exception ex = assertThrows(RuntimeException.class, () ->
          projectMemberService.removeMemberFromProject(PROJECT_ID, USER_ID, ADMIN_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("Project not found");
    }

    @Test
    void shouldFailWhenOnlyProjectAdminsCanRemove() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@test.com", "Admin");
      User userToRemove = createUser(USER_ID, "user@test.com", "User");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToRemove));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(false));

      Exception ex = assertThrows(IllegalArgumentException.class, () ->
          projectMemberService.removeMemberFromProject(PROJECT_ID, USER_ID, ADMIN_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("Only project admins can remove members");
    }

    @Test
    void shouldRemoveMemberSuccessfully() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@test.com", "Admin");
      User userToRemove = createUser(USER_ID, "user@test.com", "User");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToRemove));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(false));
      when(openFgaService.removeProjectMember(USER_ID, PROJECT_ID)).thenReturn(Completable.complete());

      projectMemberService.removeMemberFromProject(PROJECT_ID, USER_ID, ADMIN_ID).blockingAwait();

      verify(openFgaService).removeProjectMember(USER_ID, PROJECT_ID);
      verify(notificationService).sendNotification(anyString(), any());
    }

    @Test
    void shouldFailWhenRemovingLastAdmin() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@test.com", "Admin");
      User userToRemove = createUser(USER_ID, "user@test.com", "User Admin");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToRemove));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.countProjectAdmins(PROJECT_ID)).thenReturn(Single.just(1));

      Exception ex = assertThrows(IllegalStateException.class, () ->
          projectMemberService.removeMemberFromProject(PROJECT_ID, USER_ID, ADMIN_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("Cannot remove the last admin from project");
      verify(openFgaService, never()).removeProjectMember(anyString(), anyString());
    }

    @Test
    void shouldRemoveAdminSuccessfullyWhenMultipleAdmins() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@test.com", "Admin");
      User adminToRemove = createUser(USER_ID, "admin2@test.com", "Admin 2");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(adminToRemove));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.countProjectAdmins(PROJECT_ID)).thenReturn(Single.just(2));
      when(openFgaService.removeProjectMember(USER_ID, PROJECT_ID)).thenReturn(Completable.complete());

      projectMemberService.removeMemberFromProject(PROJECT_ID, USER_ID, ADMIN_ID).blockingAwait();

      verify(openFgaService).removeProjectMember(USER_ID, PROJECT_ID);
    }

    @Test
    void shouldFailWhenAdminUserNotFound() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.error(new RuntimeException("Not found")));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(createUser(USER_ID, "u@t.com", "User")));

      Exception ex = assertThrows(RuntimeException.class, () ->
          projectMemberService.removeMemberFromProject(PROJECT_ID, USER_ID, ADMIN_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("Admin user not found");
    }

    @Test
    void shouldFailWhenUserToRemoveNotFound() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(createUser(ADMIN_ID, "a@t.com", "Admin")));
      when(userService.getUserById(USER_ID)).thenReturn(Single.error(new RuntimeException("Not found")));

      Exception ex = assertThrows(RuntimeException.class, () ->
          projectMemberService.removeMemberFromProject(PROJECT_ID, USER_ID, ADMIN_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("User to remove not found");
    }
  }

  @Nested
  class LeaveProject {

    @Test
    void shouldFailWhenLeavingAsLastAdmin() {
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.countProjectAdmins(PROJECT_ID)).thenReturn(Single.just(1));

      Exception ex = assertThrows(IllegalStateException.class, () ->
          projectMemberService.leaveProject(PROJECT_ID, USER_ID).blockingAwait());

      assertThat(ex.getMessage()).contains("Cannot leave project as the last admin");
    }

    @Test
    void shouldLeaveProjectSuccessfullyWhenNotAdmin() {
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(false));
      when(openFgaService.removeProjectMember(USER_ID, PROJECT_ID)).thenReturn(Completable.complete());

      projectMemberService.leaveProject(PROJECT_ID, USER_ID).blockingAwait();

      verify(openFgaService).removeProjectMember(USER_ID, PROJECT_ID);
    }

    @Test
    void shouldLeaveProjectSuccessfullyWhenAdminWithOtherAdmins() {
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.countProjectAdmins(PROJECT_ID)).thenReturn(Single.just(2));
      when(openFgaService.removeProjectMember(USER_ID, PROJECT_ID)).thenReturn(Completable.complete());

      projectMemberService.leaveProject(PROJECT_ID, USER_ID).blockingAwait();

      verify(openFgaService).removeProjectMember(USER_ID, PROJECT_ID);
    }
  }

  @Nested
  class UpdateMemberRole {

    @Test
    void shouldRejectSelfRoleChange() {
      Exception ex = assertThrows(IllegalArgumentException.class, () ->
          projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "viewer", USER_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("You cannot change your own role");
    }

    @Test
    void shouldRejectInvalidRole() {
      Exception ex = assertThrows(IllegalArgumentException.class, () ->
          projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "owner", ADMIN_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("Invalid project role");
    }

    @Test
    void shouldFailWhenOnlyProjectAdminsCanUpdate() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@test.com", "Admin");
      User userToUpdate = createUser(USER_ID, "user@test.com", "User");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToUpdate));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(false));

      Exception ex = assertThrows(IllegalArgumentException.class, () ->
          projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "editor", ADMIN_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("Only project admins can update member roles");
    }

    @Test
    void shouldFailWhenProjectNotFound() {
      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.empty());
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(createUser(ADMIN_ID, "a@t.com", "Admin")));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(createUser(USER_ID, "u@t.com", "User")));

      Exception ex = assertThrows(RuntimeException.class, () ->
          projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "editor", ADMIN_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("Project not found");
    }

    @Test
    void shouldFailWhenAdminUserNotFound() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.error(new RuntimeException("Not found")));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(createUser(USER_ID, "u@t.com", "User")));

      Exception ex = assertThrows(RuntimeException.class, () ->
          projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "editor", ADMIN_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("Admin user not found");
    }

    @Test
    void shouldFailWhenUserToUpdateNotFound() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(createUser(ADMIN_ID, "a@t.com", "Admin")));
      when(userService.getUserById(USER_ID)).thenReturn(Single.error(new RuntimeException("Not found")));

      Exception ex = assertThrows(RuntimeException.class, () ->
          projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "editor", ADMIN_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("User to update not found");
    }

    @Test
    void shouldUpdateMemberRoleSuccessfully() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@test.com", "Admin");
      User userToUpdate = createUser(USER_ID, "user@test.com", "User");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToUpdate));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(false));
      when(openFgaService.updateProjectRole(USER_ID, PROJECT_ID, "editor"))
          .thenReturn(Completable.complete());

      projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "editor", ADMIN_ID).blockingAwait();

      verify(openFgaService).updateProjectRole(USER_ID, PROJECT_ID, "editor");
      verify(notificationService).sendNotification(anyString(), any());
    }

    @Test
    void shouldFailWhenDowngradingLastAdmin() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@test.com", "Admin");
      User userToUpdate = createUser(USER_ID, "user@test.com", "User");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToUpdate));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.countProjectAdmins(PROJECT_ID)).thenReturn(Single.just(1));

      Exception ex = assertThrows(IllegalStateException.class, () ->
          projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "editor", ADMIN_ID)
              .blockingAwait());

      assertThat(ex.getMessage()).contains("Cannot downgrade the last admin");
      verify(openFgaService, never()).updateProjectRole(anyString(), anyString(), anyString());
    }

    @Test
    void shouldUpdateAdminToEditorWhenMultipleAdmins() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@test.com", "Admin");
      User userToUpdate = createUser(USER_ID, "user@test.com", "User Admin");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToUpdate));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.countProjectAdmins(PROJECT_ID)).thenReturn(Single.just(2));
      when(openFgaService.updateProjectRole(USER_ID, PROJECT_ID, "editor"))
          .thenReturn(Completable.complete());

      projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "editor", ADMIN_ID).blockingAwait();

      verify(openFgaService).updateProjectRole(USER_ID, PROJECT_ID, "editor");
    }
  }

  @Nested
  class ListProjectMembers {

    @Test
    void shouldReturnEmptyListWhenNoMembers() {
      when(openFgaService.getProjectMembers(PROJECT_ID)).thenReturn(Single.just(new HashSet<>()));

      List<User> result = projectMemberService.listProjectMembers(PROJECT_ID, ADMIN_ID).blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnMembersWithDetails() {
      when(openFgaService.getProjectMembers(PROJECT_ID))
          .thenReturn(Single.just(Set.of(USER_ID)));
      User user = createUser(USER_ID, "user@test.com", "User");
      when(userService.getUsersByIds(any())).thenReturn(Single.just(List.of(user)));

      List<User> result = projectMemberService.listProjectMembers(PROJECT_ID, ADMIN_ID).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getUserId()).isEqualTo(USER_ID);
      assertThat(result.get(0).getEmail()).isEqualTo("user@test.com");
    }

    @Test
    void shouldPropagateErrorWhenGetProjectMembersFails() {
      when(openFgaService.getProjectMembers(PROJECT_ID))
          .thenReturn(Single.error(new RuntimeException("OpenFGA connection failed")));

      Exception ex = assertThrows(RuntimeException.class, () ->
          projectMemberService.listProjectMembers(PROJECT_ID, ADMIN_ID).blockingGet());

      assertThat(ex.getMessage()).contains("OpenFGA connection failed");
    }

    @Test
    void shouldPropagateErrorWhenGetUsersByIdsFails() {
      when(openFgaService.getProjectMembers(PROJECT_ID))
          .thenReturn(Single.just(Set.of(USER_ID)));
      when(userService.getUsersByIds(any()))
          .thenReturn(Single.error(new RuntimeException("User service unavailable")));

      Exception ex = assertThrows(RuntimeException.class, () ->
          projectMemberService.listProjectMembers(PROJECT_ID, ADMIN_ID).blockingGet());

      assertThat(ex.getMessage()).contains("User service unavailable");
    }
  }
}
