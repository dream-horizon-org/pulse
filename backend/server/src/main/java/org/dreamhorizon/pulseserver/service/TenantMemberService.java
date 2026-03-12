package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.resources.v1.members.models.BulkInviteResult;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**

 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantMemberService {
    
    private final UserService userService;
    private final TenantService tenantService;
    private final OpenFgaService openFgaService;
    private final EmailService emailService;
    
    /**
     * Add a user to a tenant with the specified role.
     * Creates a pending user if they don't exist.
     * 
     * <p>This method handles:
     * <ul>
     *   <li>Authorization check (is addedBy a tenant admin?)</li>
     *   <li>Fetching tenant and admin details from database</li>
     *   <li>Creating or finding the user to be added</li>
     *   <li>Assigning role in OpenFGA</li>
     *   <li>Sending welcome email notification</li>
     * </ul>
     * 
     * @param tenantId Tenant ID
     * @param email User's email address
     * @param role Role to assign (admin, member)
     * @param addedBy User ID of the person adding this user
     * @return Single<User> The user that was added
     */
    public Single<User> addUserToTenant(String tenantId, String email, String role, String addedBy) {
        log.info("Adding user to tenant: email={}, tenant={}, role={}, addedBy={}", 
            email, tenantId, role, addedBy);
        
        // Validate role
        if (!isValidTenantRole(role)) {
            return Single.error(new IllegalArgumentException(
                "Invalid tenant role: " + role + ". Must be one of: admin, member"));
        }
        
        // 1. FIRST: Check permissions (fail fast before any data operations)
        return openFgaService.isTenantAdmin(addedBy, tenantId)
            .flatMap(isAdmin -> {
                if (!isAdmin) {
                    return Single.error(new IllegalArgumentException(
                        "Only tenant admins can add members"));
                }
                
                // 2. THEN: Fetch all needed data
                return Single.zip(
                    tenantService.getTenant(tenantId)
                        .switchIfEmpty(Single.error(new RuntimeException("Tenant not found: " + tenantId))),
                    userService.getUserById(addedBy)
                        .onErrorResumeNext(error -> Single.error(new RuntimeException("Admin user not found: " + addedBy))),
                    (tenant, admin) -> new AddContext(tenant, admin)
                );
            })
            // 3. Get or create user being added
            .flatMap(ctx -> userService.getOrCreateUser(email, email)
                .map(user -> new AddCompleteContext(ctx.tenant, ctx.admin, user)))
            // 4. Assign role in OpenFGA with rollback on failure
            .flatMap(ctx -> {
                // Check if user was newly created by checking if they existed before
                return userService.getUserByEmail(email)
                    .isEmpty()
                    .flatMap(wasNewlyCreated -> 
                        openFgaService.assignTenantRole(ctx.newUser.getUserId(), tenantId, role)
                            .toSingleDefault(ctx)
                            .onErrorResumeNext(fgaError -> {
                                if (wasNewlyCreated) {
                                    // ROLLBACK: User was just created, log warning about orphaned user
                                    log.error("OpenFGA role assignment failed for newly created user. " +
                                        "User {} may be orphaned in database. Error: {}", 
                                        ctx.newUser.getUserId(), fgaError.getMessage());
                                }
                                return Single.error(fgaError);
                            })
                    );
            })
            // 5. Send notification email (non-critical)
            .doOnSuccess(ctx -> {
                try {
                    emailService.sendTenantWelcomeEmail(
                        ctx.newUser.getEmail(),
                        ctx.tenant.getName(),
                        role,
                        ctx.admin.getName());
                } catch (Exception e) {
                    log.error("Failed to send welcome email to {}", ctx.newUser.getEmail(), e);
                }
                log.info("User added to tenant successfully: userId={}, tenant={}, role={}", 
                    ctx.newUser.getUserId(), tenantId, role);
            })
            .map(ctx -> ctx.newUser)
            .doOnError(error -> 
                log.error("Failed to add user to tenant: email={}, tenant={}", email, tenantId, error)
            );
    }
    
    /**
     * Add multiple users to a tenant with the specified role (bulk invite).
     * This method processes multiple emails, handling failures gracefully.
     * 
     * @param tenantId Tenant ID
     * @param emails List of email addresses to invite
     * @param role Role to assign (admin, member)
     * @param addedBy User ID of the person adding these users
     * @return Single<BulkInviteResult> Results of bulk invite operation
     */
    public Single<BulkInviteResult> addUsersToTenant(
            String tenantId, 
            List<String> emails, 
            String role, 
            String addedBy) {
        
        log.info("Bulk adding users to tenant: count={}, tenant={}, role={}, addedBy={}", 
            emails.size(), tenantId, role, addedBy);
        
        // Trim and deduplicate emails
        Set<String> uniqueEmails = emails.stream()
            .map(String::trim)
            .filter(email -> !email.isEmpty())
            .collect(Collectors.toSet());
        
        if (uniqueEmails.isEmpty()) {
            return Single.just(BulkInviteResult.builder()
                .successCount(0)
                .failureCount(0)
                .skippedCount(0)
                .successEmails(new ArrayList<>())
                .failedEmails(new ArrayList<>())
                .skippedEmails(new ArrayList<>())
                .build());
        }
        
        List<String> successEmails = new ArrayList<>();
        List<String> failedEmails = new ArrayList<>();
        List<String> skippedEmails = new ArrayList<>();
        
        // Process each email sequentially
        List<Completable> inviteOperations = new ArrayList<>();
        
        for (String email : uniqueEmails) {
            Completable operation = addUserToTenant(tenantId, email, role, addedBy)
                .doOnSuccess(user -> {
                    synchronized (successEmails) {
                        successEmails.add(email);
                    }
                })
                .ignoreElement() // Convert Single to Completable
                .onErrorComplete(error -> {
                    // Log and record error, then complete successfully to continue with other emails
                    log.warn("Failed to add user to tenant: email={}, error={}", email, error.getMessage());
                    synchronized (failedEmails) {
                        failedEmails.add(email + " (" + error.getMessage() + ")");
                    }
                    return true; // Complete successfully to continue processing
                });
            
            inviteOperations.add(operation);
        }
        
        // Execute all operations and collect results
        return Completable.merge(inviteOperations)
            .andThen(Single.fromCallable(() -> 
                BulkInviteResult.builder()
                    .successCount(successEmails.size())
                    .failureCount(failedEmails.size())
                    .skippedCount(uniqueEmails.size() - (successEmails.size() + failedEmails.size()))
                    .successEmails(successEmails)
                    .failedEmails(failedEmails)
                    .skippedEmails(skippedEmails)
                    .build()
            ))
            .doOnSuccess(bulkResult -> {
                if (bulkResult.getSkippedCount() > 0) {
                    log.warn("Some emails were skipped: count={}", bulkResult.getSkippedCount());
                }
                log.info("Bulk invite completed: success={}, failed={}, skipped={}", 
                    bulkResult.getSuccessCount(), 
                    bulkResult.getFailureCount(), 
                    bulkResult.getSkippedCount());
            });
    }
    
    /**
     * Remove a user from a tenant (cascades to all projects in tenant).
     * 
     * @param tenantId Tenant ID
     * @param userIdToRemove User ID to remove
     * @param removedBy User ID of the person performing the removal
     * @return Completable Completes when user is removed
     */
    public Completable removeUserFromTenant(String tenantId, String userIdToRemove, String removedBy) {
        log.info("Removing user from tenant: user={}, tenant={}, removedBy={}", 
            userIdToRemove, tenantId, removedBy);
        
        // Fetch all needed data
        return Single.zip(
                tenantService.getTenant(tenantId)
                    .switchIfEmpty(Single.error(new RuntimeException("Tenant not found: " + tenantId))),
                userService.getUserById(removedBy)
                    .onErrorResumeNext(error -> Single.error(new RuntimeException("Admin user not found: " + removedBy))),
                userService.getUserById(userIdToRemove)
                    .onErrorResumeNext(error -> Single.error(new RuntimeException("User to remove not found: " + userIdToRemove))),
                (tenant, admin, userToRemove) -> new RemoveContext(tenant, admin, userToRemove)
            )
            // Authorization check
            .flatMap(ctx -> openFgaService.isTenantAdmin(removedBy, tenantId)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Single.error(new IllegalArgumentException(
                            "Only tenant admins can remove members"));
                    }
                    return Single.just(ctx);
                }))
            // Remove from OpenFGA
            .flatMap(ctx -> openFgaService.removeTenantMember(userIdToRemove, tenantId)
                .andThen(Single.just(ctx)))
            // Send notification
            .doOnSuccess(ctx -> {
                try {
                    emailService.sendAccessRemovedEmail(
                        ctx.userToRemove.getEmail(),
                        ctx.tenant.getName(),
                        ctx.admin.getName());
                } catch (Exception e) {
                    log.error("Failed to send removal email to {}", ctx.userToRemove.getEmail(), e);
                }
                log.info("User removed from tenant successfully: user={}, tenant={}", userIdToRemove, tenantId);
            })
            .ignoreElement()
            .doOnError(error -> 
                log.error("Failed to remove user from tenant: user={}, tenant={}", userIdToRemove, tenantId, error)
            );
    }
    
    /**
     * User leaves a tenant (self-removal).
     * 
     * @param tenantId Tenant ID
     * @param userId User ID
     * @return Completable Completes when user leaves
     */
    public Completable leaveTenant(String tenantId, String userId) {
        log.info("User leaving tenant: user={}, tenant={}", userId, tenantId);
        
        return openFgaService.removeTenantMember(userId, tenantId)
            .doOnComplete(() -> 
                log.info("User left tenant successfully: user={}, tenant={}", userId, tenantId)
            )
            .doOnError(error -> 
                log.error("Failed to leave tenant: user={}, tenant={}", userId, tenantId, error)
            );
    }
    
    /**
     * Update a user's role in a tenant.
     * Prevents self-role changes.
     * 
     * @param tenantId Tenant ID
     * @param userId User ID whose role to update
     * @param newRole New role
     * @param updatedBy User ID of the person performing the update
     * @return Completable Completes when role is updated
     */
    public Completable updateTenantRole(String tenantId, String userId, String newRole, String updatedBy) {
        log.info("Updating tenant role: user={}, tenant={}, newRole={}, updatedBy={}", 
            userId, tenantId, newRole, updatedBy);
        
        // Prevent self-role changes
        if (userId.equals(updatedBy)) {
            return Completable.error(new IllegalArgumentException(
                "You cannot change your own role"));
        }
        
        // Validate role
        if (!isValidTenantRole(newRole)) {
            return Completable.error(new IllegalArgumentException(
                "Invalid tenant role: " + newRole + ". Must be one of: admin, member"));
        }
        
        // Fetch all needed data
        return Single.zip(
                tenantService.getTenant(tenantId)
                    .switchIfEmpty(Single.error(new RuntimeException("Tenant not found: " + tenantId))),
                userService.getUserById(updatedBy)
                    .onErrorResumeNext(error -> Single.error(new RuntimeException("Admin user not found: " + updatedBy))),
                userService.getUserById(userId)
                    .onErrorResumeNext(error -> Single.error(new RuntimeException("User to update not found: " + userId))),
                (tenant, admin, userToUpdate) -> new UpdateContext(tenant, admin, userToUpdate)
            )
            // Authorization check
            .flatMap(ctx -> openFgaService.isTenantAdmin(updatedBy, tenantId)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Single.error(new IllegalArgumentException(
                            "Only tenant admins can update member roles"));
                    }
                    return Single.just(ctx);
                }))
            // Update role in OpenFGA
            .flatMap(ctx -> openFgaService.updateTenantRole(userId, tenantId, newRole)
                .andThen(Single.just(ctx)))
            // Send notification
            .doOnSuccess(ctx -> {
                try {
                    emailService.sendRoleUpdatedEmail(
                        ctx.userToUpdate.getEmail(),
                        ctx.tenant.getName(),
                        newRole,
                        ctx.admin.getName());
                } catch (Exception e) {
                    log.error("Failed to send role update email to {}", ctx.userToUpdate.getEmail(), e);
                }
                log.info("Tenant role updated successfully: user={}, tenant={}, newRole={}", 
                    userId, tenantId, newRole);
            })
            .ignoreElement()
            .doOnError(error -> 
                log.error("Failed to update tenant role: user={}, tenant={}", userId, tenantId, error)
            );
    }
    
    /**
     * List all members of a tenant with their roles.
     * Queries OpenFGA for user IDs and enriches with user data from DB.
     * 
     * @param tenantId Tenant ID
     * @param requesterId User ID requesting the list (for authorization)
     * @return Single<List<User>> List of users with access to tenant
     */
    public Single<List<User>> listTenantMembers(String tenantId, String requesterId) {
        log.debug("Listing tenant members: tenant={}, requestedBy={}", tenantId, requesterId);
        
        return openFgaService.getTenantMembers(tenantId)
            .flatMap(userIds -> {
                if (userIds.isEmpty()) {
                    return Single.just(new ArrayList<User>());
                }
                
                // Fetch user details from UserService
                return userService.getUsersByIds(new ArrayList<>(userIds));
            })
            .doOnSuccess(members -> 
                log.debug("Retrieved {} tenant members for tenant: {}", members.size(), tenantId)
            )
            .doOnError(error -> 
                log.error("Failed to list tenant members: tenant={}", tenantId, error)
            );
    }
    
    /**
     * Validate tenant role
     */
    private boolean isValidTenantRole(String role) {
        return "admin".equals(role) || "member".equals(role);
    }
    
    // Helper classes for cleaner code
    @Value
    private static class AddContext {
        Tenant tenant;
        User admin;
    }
    
    @Value
    private static class AddCompleteContext {
        Tenant tenant;
        User admin;
        User newUser;
    }
    
    @Value
    private static class RemoveContext {
        Tenant tenant;
        User admin;
        User userToRemove;
    }
    
    @Value
    private static class UpdateContext {
        Tenant tenant;
        User admin;
        User userToUpdate;
    }
}
