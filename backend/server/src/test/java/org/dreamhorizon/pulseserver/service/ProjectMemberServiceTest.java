package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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

  private static final String PROJECT_ID = "proj-123";
  private static final String TENANT_ID = "tenant-abc";
  private static final String ADMIN_ID = "user-admin";
  private static final String USER_ID = "user-1";
  private static final String EMAIL = "user@example.com";

  @BeforeEach
  void setUp() {
    projectMemberService = new ProjectMemberService(
        userService,
        projectDao,
        openFgaService,
        emailService,
        tenantMemberService,
        notificationService);

    lenient().when(notificationService.sendNotification(any(String.class), any()))
        .thenReturn(Single.just(NotificationBatchResponseDto.builder().batchId("batch-1").build()));
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
        .isActive(true)
        .build();
  }

  @Nested
  class AddMemberToProject {

    @Test
    void shouldAddMemberSuccessfully() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin");
      User newUser = createUser(USER_ID, EMAIL, "New User");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(userService.getOrCreateUser(EMAIL, EMAIL)).thenReturn(Single.just(newUser));
      when(openFgaService.getUserTenantRole(USER_ID, TENANT_ID)).thenReturn(Single.just(Optional.empty()));
      when(tenantMemberService.addUserToTenant(eq(TENANT_ID), eq(EMAIL), eq("member"), eq(ADMIN_ID)))
          .thenReturn(Single.just(newUser));
      when(openFgaService.assignProjectRole(USER_ID, PROJECT_ID, "editor")).thenReturn(Completable.complete());

      User result = projectMemberService.addMemberToProject(PROJECT_ID, EMAIL, "editor", ADMIN_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo(USER_ID);
      assertThat(result.getEmail()).isEqualTo(EMAIL);
      verify(openFgaService).assignProjectRole(USER_ID, PROJECT_ID, "editor");
    }

    @Test
    void shouldAddMemberWhenUserAlreadyInTenant() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin");
      User newUser = createUser(USER_ID, EMAIL, "New User");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(userService.getOrCreateUser(EMAIL, EMAIL)).thenReturn(Single.just(newUser));
      when(openFgaService.getUserTenantRole(USER_ID, TENANT_ID)).thenReturn(Single.just(Optional.of("member")));
      when(openFgaService.assignProjectRole(USER_ID, PROJECT_ID, "viewer")).thenReturn(Completable.complete());

      User result = projectMemberService.addMemberToProject(PROJECT_ID, EMAIL, "viewer", ADMIN_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo(USER_ID);
      verify(tenantMemberService, never()).addUserToTenant(any(), any(), any(), any());
      verify(openFgaService).assignProjectRole(USER_ID, PROJECT_ID, "viewer");
    }

    @Test
    void shouldRejectInvalidRole() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
          projectMemberService.addMemberToProject(PROJECT_ID, EMAIL, "owner", ADMIN_ID).blockingGet());

      assertThat(ex.getMessage()).contains("Invalid project role");
      assertThat(ex.getMessage()).contains("owner");
      verify(projectDao, never()).getProjectByProjectId(any());
    }

    @Test
    void shouldFailWhenProjectNotFound() {
      when(projectDao.getProjectByProjectId("unknown")).thenReturn(Maybe.empty());
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(createUser(ADMIN_ID, "a@b.com", "A")));

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          projectMemberService.addMemberToProject("unknown", EMAIL, "editor", ADMIN_ID).blockingGet());

      assertThat(ex.getMessage()).contains("Project not found");
    }

    @Test
    void shouldFailWhenNotAdmin() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(false));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
          projectMemberService.addMemberToProject(PROJECT_ID, EMAIL, "editor", ADMIN_ID).blockingGet());

      assertThat(ex.getMessage()).contains("Only project admins can add members");
      verify(openFgaService, never()).assignProjectRole(any(), any(), any());
    }
  }

  @Nested
  class RemoveMemberFromProject {

    @Test
    void shouldRemoveMemberSuccessfully() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin");
      User userToRemove = createUser(USER_ID, EMAIL, "User");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToRemove));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(false));
      when(openFgaService.removeProjectMember(USER_ID, PROJECT_ID)).thenReturn(Completable.complete());

      projectMemberService.removeMemberFromProject(PROJECT_ID, USER_ID, ADMIN_ID).blockingAwait();

      verify(openFgaService).removeProjectMember(USER_ID, PROJECT_ID);
    }

    @Test
    void shouldBlockRemovalOfLastAdmin() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User soleAdmin = createUser(ADMIN_ID, "admin@example.com", "Sole Admin");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(soleAdmin));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.countProjectAdmins(PROJECT_ID)).thenReturn(Single.just(1));

      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
          projectMemberService.removeMemberFromProject(PROJECT_ID, ADMIN_ID, ADMIN_ID).blockingAwait());

      assertThat(ex.getMessage()).contains("Cannot remove the last admin");
      verify(openFgaService, never()).removeProjectMember(any(), any());
    }
  }

  @Nested
  class LeaveProject {

    @Test
    void shouldLeaveProjectSuccessfully() {
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(false));
      when(openFgaService.removeProjectMember(USER_ID, PROJECT_ID)).thenReturn(Completable.complete());

      projectMemberService.leaveProject(PROJECT_ID, USER_ID).blockingAwait();

      verify(openFgaService).removeProjectMember(USER_ID, PROJECT_ID);
    }

    @Test
    void shouldLeaveProjectSuccessfullyWhenAdminWithOthers() {
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.countProjectAdmins(PROJECT_ID)).thenReturn(Single.just(2));
      when(openFgaService.removeProjectMember(ADMIN_ID, PROJECT_ID)).thenReturn(Completable.complete());

      projectMemberService.leaveProject(PROJECT_ID, ADMIN_ID).blockingAwait();

      verify(openFgaService).removeProjectMember(ADMIN_ID, PROJECT_ID);
    }

    @Test
    void shouldBlockLastAdminFromLeaving() {
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.countProjectAdmins(PROJECT_ID)).thenReturn(Single.just(1));

      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
          projectMemberService.leaveProject(PROJECT_ID, ADMIN_ID).blockingAwait());

      assertThat(ex.getMessage()).contains("Cannot leave project as the last admin");
      verify(openFgaService, never()).removeProjectMember(any(), any());
    }
  }

  @Nested
  class UpdateMemberRole {

    @Test
    void shouldUpdateRoleSuccessfully() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin");
      User userToUpdate = createUser(USER_ID, EMAIL, "User");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToUpdate));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(false));
      when(openFgaService.updateProjectRole(USER_ID, PROJECT_ID, "admin")).thenReturn(Completable.complete());

      projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "admin", ADMIN_ID).blockingAwait();

      verify(openFgaService).updateProjectRole(USER_ID, PROJECT_ID, "admin");
    }

    @Test
    void shouldPreventSelfRoleChange() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
          projectMemberService.updateMemberRole(PROJECT_ID, ADMIN_ID, "editor", ADMIN_ID).blockingAwait());

      assertThat(ex.getMessage()).contains("cannot change your own role");
      verify(openFgaService, never()).updateProjectRole(any(), any(), any());
    }

    @Test
    void shouldRejectInvalidRole() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
          projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "owner", ADMIN_ID).blockingAwait());

      assertThat(ex.getMessage()).contains("Invalid project role");
      verify(openFgaService, never()).updateProjectRole(any(), any(), any());
    }

    @Test
    void shouldBlockLastAdminDowngrade() {
      Project project = createProject(PROJECT_ID, TENANT_ID, "My Project");
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin");
      User adminToUpdate = createUser(USER_ID, EMAIL, "Another Admin");

      when(projectDao.getProjectByProjectId(PROJECT_ID)).thenReturn(Maybe.just(project));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(adminToUpdate));
      when(openFgaService.isProjectAdmin(ADMIN_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.isProjectAdmin(USER_ID, PROJECT_ID)).thenReturn(Single.just(true));
      when(openFgaService.countProjectAdmins(PROJECT_ID)).thenReturn(Single.just(1));

      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
          projectMemberService.updateMemberRole(PROJECT_ID, USER_ID, "editor", ADMIN_ID).blockingAwait());

      assertThat(ex.getMessage()).contains("Cannot downgrade the last admin");
      verify(openFgaService, never()).updateProjectRole(any(), any(), any());
    }
  }

  @Nested
  class ListProjectMembers {

    @Test
    void shouldReturnMembers() {
      User user1 = createUser("user-1", "u1@example.com", "User 1");
      User user2 = createUser("user-2", "u2@example.com", "User 2");
      Set<String> userIds = new HashSet<>(List.of("user-1", "user-2"));

      when(openFgaService.getProjectMembers(PROJECT_ID)).thenReturn(Single.just(userIds));
      when(userService.getUsersByIds(any())).thenReturn(Single.just(List.of(user1, user2)));

      List<User> result = projectMemberService.listProjectMembers(PROJECT_ID, ADMIN_ID).blockingGet();

      assertThat(result).hasSize(2);
      assertThat(result).extracting(User::getUserId).containsExactlyInAnyOrder("user-1", "user-2");
      verify(userService).getUsersByIds(any());
    }

    @Test
    void shouldReturnEmptyListWhenNoMembers() {
      when(openFgaService.getProjectMembers(PROJECT_ID)).thenReturn(Single.just(new HashSet<>()));

      List<User> result = projectMemberService.listProjectMembers(PROJECT_ID, ADMIN_ID).blockingGet();

      assertThat(result).isEmpty();
      verify(userService, never()).getUsersByIds(any());
    }
  }
}
