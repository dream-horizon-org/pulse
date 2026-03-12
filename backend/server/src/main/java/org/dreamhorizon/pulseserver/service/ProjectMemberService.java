package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.constant.NotificationConstants;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.resources.notification.models.RecipientsDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SendNotificationRequestDto;
import org.dreamhorizon.pulseserver.resources.v1.members.models.BulkInviteResult;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;

/**
 * Service for managing project memberships.
 * Uses OpenFGA as the single source of truth for all member relationships.
 * 
 * <p>This service follows clean architecture principles:
 * <ul>
 *   <li>Service layer fetches all needed data (no reliance on caller to provide names/details)</li>
 *   <li>Only IDs are passed as parameters (data is fetched internally)</li>
 *   <li>Business logic and authorization checks are centralized here</li>
 *   <li>Automatically ensures users are added to parent tenant when added to project</li>
 * </ul>
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectMemberService {
    
    private final UserService userService;
    private final ProjectDao projectDao;
    private final OpenFgaService openFgaService;
    private final EmailService emailService;
    private final TenantMemberService tenantMemberService;
    private final NotificationService notificationService;
    
    /**
     * Add a member to a project with the specified role.
     * Ensures user is also added to the parent tenant if not already a member.
     * 
     * <p>This method handles:
     * <ul>
     *   <li>Authorization check (is addedBy a project admin?)</li>
     *   <li>Fetching project and admin details from database</li>
     *   <li>Creating or finding the user to be added</li>
     *   <li>Ensuring user is in parent tenant</li>
     *   <li>Assigning role in OpenFGA</li>
     *   <li>Sending invite email notification</li>
     * </ul>
     * 
     * @param projectId Project ID
     * @param email User's email address
     * @param role Role to assign (admin, editor, viewer)
     * @param addedBy User ID of the person adding this user
     * @return Single<User> The user that was added
     */
    public Single<User> addMemberToProject(String projectId, String email, String role, String addedBy) {
        log.info("Adding member to project: email={}, project={}, role={}, addedBy={}", 
            email, projectId, role, addedBy);
        
        // Validate role
        if (!isValidProjectRole(role)) {
            return Single.error(new IllegalArgumentException(
                "Invalid project role: " + role + ". Must be one of: admin, editor, viewer"));
        }
        
        // 1. FIRST: Check permissions (fail fast before any data operations)
        return openFgaService.isProjectAdmin(addedBy, projectId)
            .flatMap(isAdmin -> {
                if (!isAdmin) {
                    return Single.error(new IllegalArgumentException(
                        "Only project admins can add members"));
                }
                
                // 2. THEN: Fetch all needed data
                return Single.zip(
                    projectDao.getProjectByProjectId(projectId)
                        .switchIfEmpty(Single.error(new RuntimeException("Project not found: " + projectId))),
                    userService.getUserById(addedBy)
                        .onErrorResumeNext(error -> Single.error(new RuntimeException("Admin user not found: " + addedBy))),
                    (project, admin) -> new AddContext(project, admin)
                );
            })
            // 3. Get or create user being added
            .flatMap(ctx -> userService.getOrCreateUser(email, email)
                .map(user -> new AddCompleteContext(ctx.project, ctx.admin, user)))
            // 4. Ensure user is in parent tenant
            .flatMap(ctx -> ensureUserInTenant(ctx.newUser, ctx.project.getTenantId(), addedBy)
                .andThen(Single.just(ctx)))
            // 5. Assign project role in OpenFGA with rollback awareness
            .flatMap(ctx -> {
                return userService.getUserByEmail(email)
                    .isEmpty()
                    .flatMap(wasNewlyCreated -> 
                        openFgaService.assignProjectRole(ctx.newUser.getUserId(), projectId, role)
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
            // 6. Send notification email (non-critical)
            .doOnSuccess(ctx -> {
                sendCollaboratorAddedNotification(
                    ctx.newUser.getEmail(),
                    ctx.project.getName(),
                    ctx.project.getProjectId(),
                    role,
                    ctx.admin.getName()
                );
                log.info("Member added to project successfully: userId={}, project={}, role={}", 
                    ctx.newUser.getUserId(), projectId, role);
            })
            .map(ctx -> ctx.newUser)
            .doOnError(error -> 
                log.error("Failed to add member to project: email={}, project={}", email, projectId, error)
            );
    }
    
    /**
     * Add multiple members to a project with the specified role (bulk invite).
     * This method processes multiple emails, handling failures gracefully.
     * Ensures all users are also added to the parent tenant if not already members.
     * 
     * @param projectId Project ID
     * @param emails List of email addresses to invite
     * @param role Role to assign (admin, editor, viewer)
     * @param addedBy User ID of the person adding these members
     * @return Single<BulkInviteResult> Results of bulk invite operation
     */
    public Single<BulkInviteResult> addMembersToProject(
            String projectId, 
            List<String> emails, 
            String role, 
            String addedBy) {
        
        log.info("Bulk adding members to project: count={}, project={}, role={}, addedBy={}", 
            emails.size(), projectId, role, addedBy);
        
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
            Completable operation = addMemberToProject(projectId, email, role, addedBy)
                .doOnSuccess(user -> {
                    synchronized (successEmails) {
                        successEmails.add(email);
                    }
                })
                .ignoreElement() // Convert Single to Completable
                .onErrorComplete(error -> {
                    // Log and record error, then complete successfully to continue with other emails
                    log.warn("Failed to add member to project: email={}, error={}", email, error.getMessage());
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
     * Remove a member from a project.
     * Validates that we're not removing the last admin.
     * 
     * @param projectId Project ID
     * @param userIdToRemove User ID to remove
     * @param removedBy User ID of the person performing the removal
     * @return Completable Completes when member is removed
     */
    public Completable removeMemberFromProject(String projectId, String userIdToRemove, String removedBy) {
        log.info("Removing member from project: user={}, project={}, removedBy={}", 
            userIdToRemove, projectId, removedBy);
        
        // Fetch all needed data
        return Single.zip(
                projectDao.getProjectByProjectId(projectId)
                    .switchIfEmpty(Single.error(new RuntimeException("Project not found: " + projectId))),
                userService.getUserById(removedBy)
                    .onErrorResumeNext(error -> Single.error(new RuntimeException("Admin user not found: " + removedBy))),
                userService.getUserById(userIdToRemove)
                    .onErrorResumeNext(error -> Single.error(new RuntimeException("User to remove not found: " + userIdToRemove))),
                (project, admin, userToRemove) -> new RemoveContext(project, admin, userToRemove)
            )
            // Authorization check
            .flatMap(ctx -> openFgaService.isProjectAdmin(removedBy, projectId)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Single.error(new IllegalArgumentException(
                            "Only project admins can remove members"));
                    }
                    return Single.just(ctx);
                }))
            // Check if removing an admin (need to validate admin count)
            .flatMap(ctx -> openFgaService.isProjectAdmin(userIdToRemove, projectId)
                .map(isUserToRemoveAdmin -> new RemoveValidationContext(ctx, isUserToRemoveAdmin)))
            // Validate not removing last admin
            .flatMap(ctx -> {
                if (ctx.isRemovingAdmin) {
                    return openFgaService.countProjectAdmins(projectId)
                        .flatMap(adminCount -> {
                            if (adminCount <= 1) {
                                return Single.error(new IllegalStateException(
                                    "Cannot remove the last admin from project. " +
                                    "Assign another admin first or delete the project."));
                            }
                            return Single.just(ctx.removeContext);
                        });
                } else {
                    return Single.just(ctx.removeContext);
                }
            })
            // Remove from OpenFGA
            .flatMap(ctx -> openFgaService.removeProjectMember(userIdToRemove, projectId)
                .andThen(Single.just(ctx)))
            // Send notification
            .doOnSuccess(ctx -> {
                sendCollaboratorRemovedNotification(
                    ctx.userToRemove.getEmail(),
                    ctx.project.getName(),
                    projectId,
                    ctx.admin.getName()
                );
                log.info("Member removed from project successfully: user={}, project={}", userIdToRemove, projectId);
            })
            .ignoreElement()
            .doOnError(error -> 
                log.error("Failed to remove member from project: user={}, project={}", userIdToRemove, projectId, error)
            );
    }
    
    /**
     * User leaves a project (self-removal).
     * Validates that they're not the last admin.
     * 
     * @param projectId Project ID
     * @param userId User ID
     * @return Completable Completes when user leaves
     */
    public Completable leaveProject(String projectId, String userId) {
        log.info("User leaving project: user={}, project={}", userId, projectId);
        
        return openFgaService.isProjectAdmin(userId, projectId)
            .flatMapCompletable(isAdmin -> {
                if (isAdmin) {
                    return openFgaService.countProjectAdmins(projectId)
                        .flatMapCompletable(adminCount -> {
                            if (adminCount <= 1) {
                                return Completable.error(new IllegalStateException(
                                    "Cannot leave project as the last admin. " +
                                    "Transfer admin role first or delete the project."));
                            }
                            return openFgaService.removeProjectMember(userId, projectId);
                        });
                } else {
                    return openFgaService.removeProjectMember(userId, projectId);
                }
            })
            .doOnComplete(() -> 
                log.info("User left project successfully: user={}, project={}", userId, projectId)
            )
            .doOnError(error -> 
                log.error("Failed to leave project: user={}, project={}", userId, projectId, error)
            );
    }
    
    /**
     * Update a member's role in a project.
     * Prevents self-role changes and validates admin count.
     * 
     * @param projectId Project ID
     * @param userId User ID whose role to update
     * @param newRole New role
     * @param updatedBy User ID of the person performing the update
     * @return Completable Completes when role is updated
     */
    public Completable updateMemberRole(String projectId, String userId, String newRole, String updatedBy) {
        log.info("Updating project role: user={}, project={}, newRole={}, updatedBy={}", 
            userId, projectId, newRole, updatedBy);
        
        // Prevent self-role changes
        if (userId.equals(updatedBy)) {
            return Completable.error(new IllegalArgumentException(
                "You cannot change your own role"));
        }
        
        // Validate role
        if (!isValidProjectRole(newRole)) {
            return Completable.error(new IllegalArgumentException(
                "Invalid project role: " + newRole + ". Must be one of: admin, editor, viewer"));
        }
        
        // Fetch all needed data
        return Single.zip(
                projectDao.getProjectByProjectId(projectId)
                    .switchIfEmpty(Single.error(new RuntimeException("Project not found: " + projectId))),
                userService.getUserById(updatedBy)
                    .onErrorResumeNext(error -> Single.error(new RuntimeException("Admin user not found: " + updatedBy))),
                userService.getUserById(userId)
                    .onErrorResumeNext(error -> Single.error(new RuntimeException("User to update not found: " + userId))),
                (project, admin, userToUpdate) -> new UpdateContext(project, admin, userToUpdate)
            )
            // Authorization check
            .flatMap(ctx -> openFgaService.isProjectAdmin(updatedBy, projectId)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Single.error(new IllegalArgumentException(
                            "Only project admins can update member roles"));
                    }
                    return Single.just(ctx);
                }))
            // Check if downgrading from admin
            .flatMap(ctx -> openFgaService.isProjectAdmin(userId, projectId)
                .map(isCurrentlyAdmin -> new UpdateValidationContext(ctx, isCurrentlyAdmin)))
            // Validate admin count if downgrading
            .flatMap(ctx -> {
                if (ctx.isCurrentlyAdmin && !"admin".equals(newRole)) {
                    return openFgaService.countProjectAdmins(projectId)
                        .flatMap(adminCount -> {
                            if (adminCount <= 1) {
                                return Single.error(new IllegalStateException(
                                    "Cannot downgrade the last admin. Assign another admin first."));
                            }
                            return Single.just(ctx.updateContext);
                        });
                } else {
                    return Single.just(ctx.updateContext);
                }
            })
            // Update role in OpenFGA
            .flatMap(ctx -> openFgaService.updateProjectRole(userId, projectId, newRole)
                .andThen(Single.just(ctx)))
            // Send notification
            .doOnSuccess(ctx -> {
                sendCollaboratorRoleUpdatedNotification(
                    ctx.userToUpdate.getEmail(),
                    ctx.project.getName(),
                    projectId,
                    newRole,
                    ctx.admin.getName()
                );
                log.info("Project role updated successfully: user={}, project={}, newRole={}", 
                    userId, projectId, newRole);
            })
            .ignoreElement()
            .doOnError(error -> 
                log.error("Failed to update project role: user={}, project={}", userId, projectId, error)
            );
    }
    
    /**
     * List all members of a project with their roles.
     * Queries OpenFGA for user IDs and enriches with user data from DB.
     * 
     * @param projectId Project ID
     * @param requesterId User ID requesting the list (for authorization)
     * @return Single<List<User>> List of users with access to project
     */
    public Single<List<User>> listProjectMembers(String projectId, String requesterId) {
        log.debug("Listing project members: project={}, requestedBy={}", projectId, requesterId);
        
        return openFgaService.getProjectMembers(projectId)
            .flatMap(userIds -> {
                if (userIds.isEmpty()) {
                    return Single.just(new ArrayList<User>());
                }
                
                // Fetch user details from UserService
                return userService.getUsersByIds(new ArrayList<>(userIds));
            })
            .doOnSuccess(members -> 
                log.debug("Retrieved {} project members for project: {}", members.size(), projectId)
            )
            .doOnError(error -> 
                log.error("Failed to list project members: project={}", projectId, error)
            );
    }
    
    /**
     * Ensure user is in the parent tenant.
     * If not, add them as a member automatically.
     */
    private Completable ensureUserInTenant(User user, String tenantId, String addedBy) {
        return openFgaService.getUserTenantRole(user.getUserId(), tenantId)
            .flatMapCompletable(roleOpt -> {
                if (roleOpt.isPresent()) {
                    // User already in tenant
                    log.debug("User already in tenant: user={}, tenant={}, role={}", 
                        user.getUserId(), tenantId, roleOpt.get());
                    return Completable.complete();
                } else {
                    // Add user to tenant as member
                    log.info("Auto-adding user to tenant as member: user={}, tenant={}", 
                        user.getUserId(), tenantId);
                    return tenantMemberService.addUserToTenant(
                        tenantId, user.getEmail(), "member", addedBy
                    ).ignoreElement();
                }
            });
    }
    
    /**
     * Validate project role
     */
    private boolean isValidProjectRole(String role) {
        return "admin".equals(role) || "editor".equals(role) || "viewer".equals(role);
    }
    
    /**
     * Sends notification when a collaborator is added to a project.
     * Fire-and-forget: failures are logged but don't break the main flow.
     */
    private void sendCollaboratorAddedNotification(
            String recipientEmail, 
            String projectName, 
            String projectId,
            String role, 
            String addedByName) {
        try {
            SendNotificationRequestDto request = SendNotificationRequestDto.builder()
                .eventName(NotificationConstants.Platform.EVENT_COLLABORATOR_ADDED)
                .recipients(RecipientsDto.builder()
                    .emails(List.of(recipientEmail))
                    .build())
                .channelTypes(List.of(ChannelType.EMAIL))
                    .idempotencyKey(UUID.randomUUID().toString())
                .params(Map.of(
                    "projectName", projectName,
                    "projectId", projectId,
                    "role", role,
                    "addedBy", addedByName
                ))
                .build();
            
            notificationService.sendNotificationAsync(projectId, request)
                .doOnError(error -> log.error(
                    "Failed to send collaborator added notification to {}: {}", 
                    recipientEmail, error.getMessage()))
                .subscribe();
        } catch (Exception e) {
            log.error("Failed to send collaborator added notification to {}", recipientEmail, e);
        }
    }
    
    /**
     * Sends notification when a collaborator is removed from a project.
     * Fire-and-forget: failures are logged but don't break the main flow.
     */
    private void sendCollaboratorRemovedNotification(
            String recipientEmail, 
            String projectName, 
            String projectId,
            String removedByName) {
        try {
            SendNotificationRequestDto request = SendNotificationRequestDto.builder()
                .eventName(NotificationConstants.Platform.EVENT_COLLABORATOR_REMOVED)
                .recipients(RecipientsDto.builder()
                    .emails(List.of(recipientEmail))
                    .build())
                .channelTypes(List.of(ChannelType.EMAIL))
                    .idempotencyKey(UUID.randomUUID().toString())
                .params(Map.of(
                    "projectName", projectName,
                    "removedBy", removedByName
                ))
                .build();
            
            notificationService.sendNotificationAsync(projectId, request)
                .doOnError(error -> log.error(
                    "Failed to send collaborator removed notification to {}: {}", 
                    recipientEmail, error.getMessage()))
                .subscribe();
        } catch (Exception e) {
            log.error("Failed to send collaborator removed notification to {}", recipientEmail, e);
        }
    }
    
    /**
     * Sends notification when a collaborator's role is updated.
     * Fire-and-forget: failures are logged but don't break the main flow.
     */
    private void sendCollaboratorRoleUpdatedNotification(
            String recipientEmail, 
            String projectName, 
            String projectId,
            String newRole, 
            String updatedByName) {
        try {
            SendNotificationRequestDto request = SendNotificationRequestDto.builder()
                .eventName(NotificationConstants.Platform.EVENT_COLLABORATOR_ROLE_UPDATED)
                .recipients(RecipientsDto.builder()
                    .emails(List.of(recipientEmail))
                    .build())
                .channelTypes(List.of(ChannelType.EMAIL))

                .params(Map.of(
                    "projectName", projectName,
                    "newRole", newRole,
                    "updatedBy", updatedByName
                ))
                .build();
            
            notificationService.sendNotificationAsync(projectId, request)
                .doOnError(error -> log.error(
                    "Failed to send role updated notification to {}: {}", 
                    recipientEmail, error.getMessage()))
                .subscribe();
        } catch (Exception e) {
            log.error("Failed to send role updated notification to {}", recipientEmail, e);
        }
    }
    
    // Helper classes for cleaner code
    @Value
    private static class AddContext {
        Project project;
        User admin;
    }
    
    @Value
    private static class AddCompleteContext {
        Project project;
        User admin;
        User newUser;
    }
    
    @Value
    private static class RemoveContext {
        Project project;
        User admin;
        User userToRemove;
    }
    
    @Value
    private static class RemoveValidationContext {
        RemoveContext removeContext;
        boolean isRemovingAdmin;
    }
    
    @Value
    private static class UpdateContext {
        Project project;
        User admin;
        User userToUpdate;
    }
    
    @Value
    private static class UpdateValidationContext {
        UpdateContext updateContext;
        boolean isCurrentlyAdmin;
    }
}
