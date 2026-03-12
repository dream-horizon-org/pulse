package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.user.UserDao;
import org.dreamhorizon.pulseserver.dto.UserProfileDto;
import org.dreamhorizon.pulseserver.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for user management and profile operations.
 * Integrates with OpenFGA for role and permission management.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class UserService {
    
    private final UserDao userDao;
    private final OpenFgaService openFgaService;
    
    /**
     * Get an existing user by email or create a new user if not found.
     * This is the primary method used during Google OAuth authentication and onboarding.
     * 
     * IMPROVEMENT: Now stores Firebase UID for better user identification and to prevent
     * ID mismatch issues between login and onboarding flows.
     * 
     * @param email User email from Google OAuth
     * @param name User display name
     * @param firebaseUid Firebase User ID (optional, but recommended for consistency)
     * @return Single<User> The existing or newly created user
     */
    public Single<User> getOrCreateUser(String email, String name, String firebaseUid) {
        return userDao.getUserByEmail(email)
            .switchIfEmpty(Single.defer(() -> {
                String userId = "user-" + UUID.randomUUID().toString();
                User newUser = User.builder()
                    .userId(userId)
                    .email(email)
                    .name(name)
                    .firebaseUid(firebaseUid)  // Store Firebase UID for reference
                    .isActive(true)
                    .build();
                
                log.info("Creating new user: email={}, userId={}, firebaseUid={}", email, userId, firebaseUid);
                return userDao.createUser(newUser);
            }))
            .doOnSuccess(user -> 
                log.debug("User retrieved: userId={}, email={}, firebaseUid={}", 
                    user.getUserId(), user.getEmail(), user.getFirebaseUid())
            );
    }
    
    /**
     * Overload for backward compatibility (when firebaseUid is not available).
     * 
     * @param email User email
     * @param name User display name
     * @return Single<User> The existing or newly created user
     */
    public Single<User> getOrCreateUser(String email, String name) {
        return getOrCreateUser(email, name, null);
    }
    
    /**
     * Check if a user needs to complete onboarding.
     * A user needs onboarding if they don't belong to any tenant.
     * 
     * @param userId User ID
     * @return Single<Boolean> true if user needs onboarding (has no tenants)
     */
    public Single<Boolean> needsOnboarding(String userId) {
        return openFgaService.getUserTenants(userId)
            .map(tenants -> {
                boolean needsOnboarding = tenants.isEmpty();
                log.debug("Onboarding check: userId={}, needsOnboarding={}", userId, needsOnboarding);
                return needsOnboarding;
            })
            .doOnError(error -> 
                log.error("Failed to check onboarding status for user: {}", userId, error)
            );
    }
    
    /**
     * Get user profile with tenant role information.
     * 
     * @param userId User ID
     * @param tenantId Tenant ID
     * @return Single<UserProfileDto> User profile with role information
     */
    public Single<UserProfileDto> getUserProfile(String userId, String tenantId) {
        return userDao.getUserById(userId)
            .switchIfEmpty(Single.error(new RuntimeException("User not found: " + userId)))
            .flatMap(user -> 
                openFgaService.isTenantAdmin(userId, tenantId)
                    .map(isAdmin -> {
                        String tenantRole = isAdmin ? "admin" : "member";
                        
                        return UserProfileDto.builder()
                            .userId(user.getUserId())
                            .email(user.getEmail())
                            .name(user.getName())
                            .tenantRole(tenantRole)
                            .isActive(user.getIsActive())
                            .build();
                    })
            )
            .doOnSuccess(profile -> 
                log.debug("User profile retrieved: userId={}, tenantRole={}", 
                    userId, profile.getTenantRole())
            )
            .doOnError(error -> 
                log.error("Failed to get user profile: userId={}, tenantId={}", 
                    userId, tenantId, error)
            );
    }
    
    /**
     * Update user profile information.
     * 
     * @param userId User ID
     * @param name Updated name
     * @return Single<User> Updated user
     */
    public Single<User> updateUserProfile(String userId, String name) {
        return userDao.updateUser(userId, name)
            .andThen(userDao.getUserById(userId))
            .switchIfEmpty(Single.error(new RuntimeException("User not found after update: " + userId)))
            .doOnSuccess(user -> 
                log.info("User profile updated: userId={}", userId)
            )
            .doOnError(error -> 
                log.error("Failed to update user profile: userId={}", userId, error)
            );
    }
    
    /**
     * Get user by user ID.
     * 
     * @param userId User ID
     * @return Single<User> The user
     */
    public Single<User> getUserById(String userId) {
        return userDao.getUserById(userId)
            .switchIfEmpty(Single.error(new RuntimeException("User not found: " + userId)));
    }
    
    /**
     * Get user by email.
     * Returns Maybe instead of throwing error to allow empty handling.
     * 
     * @param email User email
     * @return Maybe<User> The user if found
     */
    public Maybe<User> getUserByEmail(String email) {
        return userDao.getUserByEmail(email);
    }
    
    /**
     * Activate a pending user on first login.
     * Updates status, firebase UID, name, and last login timestamp.
     * 
     * @param userId User ID
     * @param firebaseUid Firebase UID from authentication
     * @param name User's full name
     * @return Completable that completes when activation is successful
     */
    public Completable activateUser(String userId, String firebaseUid, String name) {
        return userDao.activateUser(userId, firebaseUid, name)
            .doOnComplete(() -> 
                log.info("User activated successfully: userId={}, firebaseUid={}", userId, firebaseUid)
            )
            .doOnError(error -> 
                log.error("Failed to activate user: userId={}", userId, error)
            );
    }
    
    /**
     * Update last login timestamp for a user.
     * Called after successful authentication.
     * 
     * @param userId User ID
     * @return Completable that completes when update is successful
     */
    public Completable updateLastLogin(String userId) {
        return userDao.updateLastLogin(userId)
            .doOnComplete(() -> 
                log.debug("Last login updated: userId={}", userId)
            )
            .doOnError(error -> 
                log.error("Failed to update last login: userId={}", userId, error)
            );
    }
    
    /**
     * Get multiple users by their IDs.
     * Silently skips users that don't exist instead of failing.
     * 
     * @param userIds List of user IDs
     * @return Single<List<User>> List of users (missing users are omitted)
     */
    public Single<List<User>> getUsersByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Single.just(new ArrayList<>());
        }
        
        return Flowable.fromIterable(userIds)
            .flatMapMaybe(userId -> 
                userDao.getUserById(userId)
                    .doOnError(error -> log.warn("User not found: {}", userId))
                    .onErrorResumeNext(error -> Maybe.empty())
            )
            .toList()
            .map(users -> (List<User>) users)
            .doOnSuccess(users -> 
                log.debug("Retrieved {} users out of {} requested", users.size(), userIds.size())
            );
    }
}
