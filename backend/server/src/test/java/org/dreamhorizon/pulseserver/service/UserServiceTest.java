package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.user.UserDao;
import org.dreamhorizon.pulseserver.dto.UserProfileDto;
import org.dreamhorizon.pulseserver.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  UserDao userDao;

  @Mock
  OpenFgaService openFgaService;

  UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userDao, openFgaService);
  }

  private User createUser(String userId, String email, String name, String firebaseUid) {
    return User.builder()
        .userId(userId)
        .email(email)
        .name(name)
        .firebaseUid(firebaseUid)
        .isActive(true)
        .build();
  }

  @Nested
  class GetOrCreateUser {

    @Test
    void shouldReturnExistingUserWhenEmailExists() {
      String email = "user@example.com";
      String name = "Test User";
      String firebaseUid = "firebase-123";
      User existingUser = createUser("user-existing", email, name, firebaseUid);

      when(userDao.getUserByEmail(email)).thenReturn(Maybe.just(existingUser));

      User result = userService.getOrCreateUser(email, name, firebaseUid).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo("user-existing");
      assertThat(result.getEmail()).isEqualTo(email);
      assertThat(result.getName()).isEqualTo(name);
      verify(userDao).getUserByEmail(email);
    }

    @Test
    void shouldCreateNewUserWhenEmailDoesNotExist() {
      String email = "new@example.com";
      String name = "New User";
      String firebaseUid = "firebase-456";
      User newUser = createUser("user-new-uuid", email, name, firebaseUid);

      when(userDao.getUserByEmail(email)).thenReturn(Maybe.empty());
      when(userDao.createUser(any())).thenReturn(Single.just(newUser));

      User result = userService.getOrCreateUser(email, name, firebaseUid).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).startsWith("user-");
      assertThat(result.getEmail()).isEqualTo(email);
      assertThat(result.getName()).isEqualTo(name);

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userDao).createUser(captor.capture());
      User captured = captor.getValue();
      assertThat(captured.getEmail()).isEqualTo(email);
      assertThat(captured.getName()).isEqualTo(name);
      assertThat(captured.getFirebaseUid()).isEqualTo(firebaseUid);
      assertThat(captured.getIsActive()).isTrue();
    }

    @Test
    void shouldDelegateToThreeArgOverloadWithNullFirebaseUid() {
      String email = "user@example.com";
      String name = "Test User";
      User existingUser = createUser("user-1", email, name, null);

      when(userDao.getUserByEmail(email)).thenReturn(Maybe.just(existingUser));

      User result = userService.getOrCreateUser(email, name).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo("user-1");
      assertThat(result.getFirebaseUid()).isNull();
    }
  }

  @Nested
  class NeedsOnboarding {

    @Test
    void shouldReturnTrueWhenUserHasNoTenants() {
      String userId = "user-1";
      when(openFgaService.getUserTenants(userId)).thenReturn(Single.just(Collections.emptyList()));

      Boolean result = userService.needsOnboarding(userId).blockingGet();

      assertThat(result).isTrue();
      verify(openFgaService).getUserTenants(userId);
    }

    @Test
    void shouldReturnFalseWhenUserHasTenants() {
      String userId = "user-1";
      List<String> tenants = Arrays.asList("tenant-1", "tenant-2");
      when(openFgaService.getUserTenants(userId)).thenReturn(Single.just(tenants));

      Boolean result = userService.needsOnboarding(userId).blockingGet();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class GetUserProfile {

    @Test
    void shouldReturnProfileWithAdminRoleWhenUserIsTenantAdmin() {
      String userId = "user-1";
      String tenantId = "tenant-1";
      User user = createUser(userId, "admin@example.com", "Admin User", "firebase-1");

      when(userDao.getUserById(userId)).thenReturn(Maybe.just(user));
      when(openFgaService.isTenantAdmin(userId, tenantId)).thenReturn(Single.just(true));

      UserProfileDto result = userService.getUserProfile(userId, tenantId).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo(userId);
      assertThat(result.getEmail()).isEqualTo("admin@example.com");
      assertThat(result.getName()).isEqualTo("Admin User");
      assertThat(result.getTenantRole()).isEqualTo("admin");
      assertThat(result.getIsActive()).isTrue();
    }

    @Test
    void shouldReturnProfileWithMemberRoleWhenUserIsNotTenantAdmin() {
      String userId = "user-2";
      String tenantId = "tenant-1";
      User user = createUser(userId, "member@example.com", "Member User", "firebase-2");

      when(userDao.getUserById(userId)).thenReturn(Maybe.just(user));
      when(openFgaService.isTenantAdmin(userId, tenantId)).thenReturn(Single.just(false));

      UserProfileDto result = userService.getUserProfile(userId, tenantId).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTenantRole()).isEqualTo("member");
    }

    @Test
    void shouldThrowWhenUserNotFound() {
      String userId = "user-nonexistent";
      String tenantId = "tenant-1";

      when(userDao.getUserById(userId)).thenReturn(Maybe.empty());

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          userService.getUserProfile(userId, tenantId).blockingGet());

      assertThat(ex.getMessage()).contains("User not found");
      assertThat(ex.getMessage()).contains(userId);
    }
  }

  @Nested
  class UpdateUserProfile {

    @Test
    void shouldUpdateAndReturnUserOnSuccess() {
      String userId = "user-1";
      String newName = "Updated Name";
      User updatedUser = createUser(userId, "user@example.com", newName, "firebase-1");

      when(userDao.updateUser(userId, newName)).thenReturn(Completable.complete());
      when(userDao.getUserById(userId)).thenReturn(Maybe.just(updatedUser));

      User result = userService.updateUserProfile(userId, newName).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo(newName);
      verify(userDao).updateUser(userId, newName);
      verify(userDao).getUserById(userId);
    }

    @Test
    void shouldThrowWhenUserNotFoundAfterUpdate() {
      String userId = "user-nonexistent";
      String newName = "Updated Name";

      when(userDao.updateUser(userId, newName)).thenReturn(Completable.complete());
      when(userDao.getUserById(userId)).thenReturn(Maybe.empty());

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          userService.updateUserProfile(userId, newName).blockingGet());

      assertThat(ex.getMessage()).contains("User not found after update");
    }

    @Test
    void shouldThrowWhenUpdateFails() {
      String userId = "user-1";
      String newName = "Updated Name";

      when(userDao.updateUser(userId, newName))
          .thenReturn(Completable.error(new RuntimeException("User not found: " + userId)));

      assertThrows(RuntimeException.class, () ->
          userService.updateUserProfile(userId, newName).blockingGet());
    }
  }

  @Nested
  class GetUserById {

    @Test
    void shouldReturnUserWhenFound() {
      String userId = "user-1";
      User user = createUser(userId, "user@example.com", "Test User", null);

      when(userDao.getUserById(userId)).thenReturn(Maybe.just(user));

      User result = userService.getUserById(userId).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo(userId);
    }

    @Test
    void shouldThrowWhenUserNotFound() {
      String userId = "user-nonexistent";

      when(userDao.getUserById(userId)).thenReturn(Maybe.empty());

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          userService.getUserById(userId).blockingGet());

      assertThat(ex.getMessage()).contains("User not found");
      assertThat(ex.getMessage()).contains(userId);
    }
  }

  @Nested
  class GetUserByEmail {

    @Test
    void shouldReturnUserWhenFound() {
      String email = "user@example.com";
      User user = createUser("user-1", email, "Test User", null);

      when(userDao.getUserByEmail(email)).thenReturn(Maybe.just(user));

      User result = userService.getUserByEmail(email).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getEmail()).isEqualTo(email);
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
      String email = "nonexistent@example.com";

      when(userDao.getUserByEmail(email)).thenReturn(Maybe.empty());

      userService.getUserByEmail(email)
          .test()
          .assertNoValues()
          .assertComplete();
    }
  }

  @Nested
  class ActivateUser {

    @Test
    void shouldCallDaoActivateUser() {
      String userId = "user-1";
      String firebaseUid = "firebase-123";
      String name = "Activated User";

      when(userDao.activateUser(userId, firebaseUid, name)).thenReturn(Completable.complete());

      userService.activateUser(userId, firebaseUid, name).blockingAwait();

      verify(userDao).activateUser(userId, firebaseUid, name);
    }
  }

  @Nested
  class UpdateLastLogin {

    @Test
    void shouldCallDaoUpdateLastLogin() {
      String userId = "user-1";

      when(userDao.updateLastLogin(userId)).thenReturn(Completable.complete());

      userService.updateLastLogin(userId).blockingAwait();

      verify(userDao).updateLastLogin(userId);
    }
  }

  @Nested
  class GetUsersByIds {

    @Test
    void shouldReturnEmptyListWhenUserIdsNull() {
      List<User> result = userService.getUsersByIds(null).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenUserIdsEmpty() {
      List<User> result = userService.getUsersByIds(Collections.emptyList()).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnUsersWhenAllIdsExist() {
      String userId1 = "user-1";
      String userId2 = "user-2";
      User user1 = createUser(userId1, "user1@example.com", "User 1", null);
      User user2 = createUser(userId2, "user2@example.com", "User 2", null);

      when(userDao.getUserById(userId1)).thenReturn(Maybe.just(user1));
      when(userDao.getUserById(userId2)).thenReturn(Maybe.just(user2));

      List<User> result = userService.getUsersByIds(Arrays.asList(userId1, userId2)).blockingGet();

      assertThat(result).hasSize(2);
      assertThat(result).extracting(User::getUserId).containsExactlyInAnyOrder(userId1, userId2);
    }

    @Test
    void shouldSkipMissingUsersAndReturnOnlyFoundOnes() {
      String userId1 = "user-1";
      String userId2 = "user-missing";
      String userId3 = "user-3";
      User user1 = createUser(userId1, "user1@example.com", "User 1", null);
      User user3 = createUser(userId3, "user3@example.com", "User 3", null);

      when(userDao.getUserById(userId1)).thenReturn(Maybe.just(user1));
      when(userDao.getUserById(userId2)).thenReturn(Maybe.empty());
      when(userDao.getUserById(userId3)).thenReturn(Maybe.just(user3));

      List<User> result = userService.getUsersByIds(Arrays.asList(userId1, userId2, userId3)).blockingGet();

      assertThat(result).hasSize(2);
      assertThat(result).extracting(User::getUserId).containsExactlyInAnyOrder(userId1, userId3);
    }

    @Test
    void shouldSkipUsersThatCauseError() {
      String userId1 = "user-1";
      String userId2 = "user-error";
      User user1 = createUser(userId1, "user1@example.com", "User 1", null);

      when(userDao.getUserById(userId1)).thenReturn(Maybe.just(user1));
      when(userDao.getUserById(userId2)).thenReturn(Maybe.error(new RuntimeException("DB error")));

      List<User> result = userService.getUsersByIds(Arrays.asList(userId1, userId2)).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getUserId()).isEqualTo(userId1);
    }
  }
}
