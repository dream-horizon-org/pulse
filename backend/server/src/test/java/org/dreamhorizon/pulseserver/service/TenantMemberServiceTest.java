package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Set;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantMemberServiceTest {

  @Mock
  UserService userService;

  @Mock
  TenantService tenantService;

  @Mock
  OpenFgaService openFgaService;

  @Mock
  EmailService emailService;

  TenantMemberService tenantMemberService;

  private static final String TENANT_ID = "tenant-1";
  private static final String ADMIN_ID = "admin-1";
  private static final String USER_ID = "user-1";
  private static final String EMAIL = "user@example.com";
  private static final String TENANT_NAME = "Test Org";

  @BeforeEach
  void setUp() {
    tenantMemberService = new TenantMemberService(
        userService,
        tenantService,
        openFgaService,
        emailService);
  }

  private User createUser(String userId, String email, String name) {
    return User.builder()
        .userId(userId)
        .email(email)
        .name(name)
        .isActive(true)
        .build();
  }

  private Tenant createTenant(String tenantId, String name) {
    return Tenant.builder()
        .tenantId(tenantId)
        .name(name)
        .description("Description")
        .tierId(1)
        .isActive(true)
        .build();
  }

  @Nested
  class AddUserToTenant {

    @Test
    void shouldAddUserSuccessfully() {
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin User");
      User newUser = createUser(USER_ID, EMAIL, EMAIL);
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME);

      when(tenantService.getTenant(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(openFgaService.isTenantAdmin(ADMIN_ID, TENANT_ID)).thenReturn(Single.just(true));
      when(userService.getOrCreateUser(EMAIL, EMAIL)).thenReturn(Single.just(newUser));
      when(openFgaService.assignTenantRole(USER_ID, TENANT_ID, "member")).thenReturn(Completable.complete());

      User result = tenantMemberService.addUserToTenant(TENANT_ID, EMAIL, "member", ADMIN_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo(USER_ID);
      assertThat(result.getEmail()).isEqualTo(EMAIL);
      verify(openFgaService).assignTenantRole(USER_ID, TENANT_ID, "member");
      verify(emailService).sendTenantWelcomeEmail(EMAIL, TENANT_NAME, "member", "Admin User");
    }

    @Test
    void shouldFailWhenRoleInvalid() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
          tenantMemberService.addUserToTenant(TENANT_ID, EMAIL, "owner", ADMIN_ID).blockingGet());

      assertThat(ex.getMessage()).contains("Invalid tenant role");
      assertThat(ex.getMessage()).contains("owner");
      verify(tenantService, never()).getTenant(any());
    }

    @Test
    void shouldFailWhenTenantNotFound() {
      when(tenantService.getTenant(TENANT_ID)).thenReturn(Maybe.empty());
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(createUser(ADMIN_ID, "a@b.c", "Admin")));

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          tenantMemberService.addUserToTenant(TENANT_ID, EMAIL, "member", ADMIN_ID).blockingGet());

      assertThat(ex.getMessage()).contains("Tenant not found");
      assertThat(ex.getMessage()).contains(TENANT_ID);
      verify(openFgaService, never()).assignTenantRole(any(), any(), any());
    }

    @Test
    void shouldFailWhenNotAdmin() {
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin User");
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME);

      when(tenantService.getTenant(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(openFgaService.isTenantAdmin(ADMIN_ID, TENANT_ID)).thenReturn(Single.just(false));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
          tenantMemberService.addUserToTenant(TENANT_ID, EMAIL, "member", ADMIN_ID).blockingGet());

      assertThat(ex.getMessage()).contains("Only tenant admins can add members");
      verify(userService, never()).getOrCreateUser(any(), any());
      verify(openFgaService, never()).assignTenantRole(any(), any(), any());
    }

    @Test
    void shouldFailWhenGetOrCreateUserFails() {
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin User");
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME);

      when(tenantService.getTenant(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(openFgaService.isTenantAdmin(ADMIN_ID, TENANT_ID)).thenReturn(Single.just(true));
      when(userService.getOrCreateUser(EMAIL, EMAIL))
          .thenReturn(Single.error(new RuntimeException("DAO error: connection failed")));

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          tenantMemberService.addUserToTenant(TENANT_ID, EMAIL, "member", ADMIN_ID).blockingGet());

      assertThat(ex.getMessage()).contains("DAO error");
      verify(openFgaService, never()).assignTenantRole(any(), any(), any());
    }
  }

  @Nested
  class RemoveUserFromTenant {

    @Test
    void shouldRemoveUserSuccessfully() {
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin User");
      User userToRemove = createUser(USER_ID, EMAIL, "User To Remove");
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME);

      when(tenantService.getTenant(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToRemove));
      when(openFgaService.isTenantAdmin(ADMIN_ID, TENANT_ID)).thenReturn(Single.just(true));
      when(openFgaService.removeTenantMember(USER_ID, TENANT_ID)).thenReturn(Completable.complete());

      tenantMemberService.removeUserFromTenant(TENANT_ID, USER_ID, ADMIN_ID).blockingAwait();

      verify(openFgaService).removeTenantMember(USER_ID, TENANT_ID);
      verify(emailService).sendAccessRemovedEmail(EMAIL, TENANT_NAME, "Admin User");
    }

    @Test
    void shouldFailWhenNotAdmin() {
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin User");
      User userToRemove = createUser(USER_ID, EMAIL, "User");
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME);

      when(tenantService.getTenant(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToRemove));
      when(openFgaService.isTenantAdmin(ADMIN_ID, TENANT_ID)).thenReturn(Single.just(false));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
          tenantMemberService.removeUserFromTenant(TENANT_ID, USER_ID, ADMIN_ID).blockingAwait());

      assertThat(ex.getMessage()).contains("Only tenant admins can remove members");
      verify(openFgaService, never()).removeTenantMember(any(), any());
    }
  }

  @Nested
  class LeaveTenant {

    @Test
    void shouldLeaveTenantSuccessfully() {
      when(openFgaService.removeTenantMember(USER_ID, TENANT_ID)).thenReturn(Completable.complete());

      tenantMemberService.leaveTenant(TENANT_ID, USER_ID).blockingAwait();

      verify(openFgaService).removeTenantMember(USER_ID, TENANT_ID);
    }
  }

  @Nested
  class UpdateTenantRole {

    @Test
    void shouldUpdateRoleSuccessfully() {
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin User");
      User userToUpdate = createUser(USER_ID, EMAIL, "User");
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME);

      when(tenantService.getTenant(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToUpdate));
      when(openFgaService.isTenantAdmin(ADMIN_ID, TENANT_ID)).thenReturn(Single.just(true));
      when(openFgaService.updateTenantRole(USER_ID, TENANT_ID, "admin")).thenReturn(Completable.complete());

      tenantMemberService.updateTenantRole(TENANT_ID, USER_ID, "admin", ADMIN_ID).blockingAwait();

      verify(openFgaService).updateTenantRole(USER_ID, TENANT_ID, "admin");
      verify(emailService).sendRoleUpdatedEmail(EMAIL, TENANT_NAME, "admin", "Admin User");
    }

    @Test
    void shouldFailWhenSelfRoleChange() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
          tenantMemberService.updateTenantRole(TENANT_ID, USER_ID, "member", USER_ID).blockingAwait());

      assertThat(ex.getMessage()).contains("cannot change your own role");
      verify(openFgaService, never()).updateTenantRole(any(), any(), any());
    }

    @Test
    void shouldFailWhenRoleInvalid() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
          tenantMemberService.updateTenantRole(TENANT_ID, USER_ID, "superuser", ADMIN_ID).blockingAwait());

      assertThat(ex.getMessage()).contains("Invalid tenant role");
      assertThat(ex.getMessage()).contains("superuser");
      verify(openFgaService, never()).updateTenantRole(any(), any(), any());
    }

    @Test
    void shouldFailWhenNotAdmin() {
      User admin = createUser(ADMIN_ID, "admin@example.com", "Admin User");
      User userToUpdate = createUser(USER_ID, EMAIL, "User");
      Tenant tenant = createTenant(TENANT_ID, TENANT_NAME);

      when(tenantService.getTenant(TENANT_ID)).thenReturn(Maybe.just(tenant));
      when(userService.getUserById(ADMIN_ID)).thenReturn(Single.just(admin));
      when(userService.getUserById(USER_ID)).thenReturn(Single.just(userToUpdate));
      when(openFgaService.isTenantAdmin(ADMIN_ID, TENANT_ID)).thenReturn(Single.just(false));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
          tenantMemberService.updateTenantRole(TENANT_ID, USER_ID, "admin", ADMIN_ID).blockingAwait());

      assertThat(ex.getMessage()).contains("Only tenant admins can update member roles");
      verify(openFgaService, never()).updateTenantRole(any(), any(), any());
    }
  }

  @Nested
  class ListTenantMembers {

    @Test
    void shouldReturnMembers() {
      User user1 = createUser("user-1", "u1@example.com", "User 1");
      User user2 = createUser("user-2", "u2@example.com", "User 2");
      Set<String> userIds = Set.of("user-1", "user-2");

      when(openFgaService.getTenantMembers(TENANT_ID)).thenReturn(Single.just(userIds));
      when(userService.getUsersByIds(anyList())).thenReturn(Single.just(List.of(user1, user2)));

      List<User> result = tenantMemberService.listTenantMembers(TENANT_ID, ADMIN_ID).blockingGet();

      assertThat(result).hasSize(2);
      assertThat(result).extracting(User::getUserId).containsExactlyInAnyOrder("user-1", "user-2");
      verify(openFgaService).getTenantMembers(TENANT_ID);
      verify(userService).getUsersByIds(anyList());
    }

    @Test
    void shouldReturnEmptyListWhenNoMembers() {
      when(openFgaService.getTenantMembers(TENANT_ID)).thenReturn(Single.just(Set.of()));

      List<User> result = tenantMemberService.listTenantMembers(TENANT_ID, ADMIN_ID).blockingGet();

      assertThat(result).isEmpty();
      verify(openFgaService).getTenantMembers(TENANT_ID);
      verify(userService, never()).getUsersByIds(anyList());
    }
  }
}
