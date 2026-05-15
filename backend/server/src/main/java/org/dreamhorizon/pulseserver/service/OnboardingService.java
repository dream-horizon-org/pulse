package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dto.request.ReqUserInfo;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;
import org.dreamhorizon.pulseserver.service.tier.TierService;

/**
 * Service for onboarding new users.
 * Handles the first-time user flow:
 * 1. Create organization (tenant) + first project atomically via TenantService
 * 2. Assign roles in OpenFGA (user as tenant admin, user as project admin)
 * 3. Generate JWT tokens with tenantId
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class OnboardingService {

  private final TenantService tenantService;
  private final OpenFgaService openFgaService;
  private final JwtService jwtService;
  private final UserService userService;
  private final TierService tierService;

  /**
   * Complete onboarding for a new user.
   * Creates a tenant and first project atomically, sets up authorization.
   *
   * IMPORTANT: This method receives Firebase UID but must use database userId for role assignments.
   * It looks up the user by email (created during login) to get the correct database userId.
   *
   * RESTRICTION: Users can only be part of ONE organization during onboarding.
   * If user is already part of any tenant (as admin or member), onboarding will fail.
   * Users must not have any existing tenant associations to onboard.
   *
   * @param firebaseUid Firebase User ID (from Firebase token)
   * @param email User email
   * @param name User display name
   * @param organizationName Organization/tenant name
   * @param projectName First project name
   * @param projectDescription First project description
   * @return OnboardingResult with tenant, project, and JWT tokens
   */
  public Single<OnboardingResult> completeOnboarding(
      String firebaseUid,
      String email,
      String name,
      String organizationName,
      String projectName,
      String projectDescription) {

    log.info("Starting onboarding: firebaseUid={}, email={}, org={}, project={}",
        firebaseUid, email, organizationName, projectName);

    // CRITICAL FIX: Look up the database user by email (created during login)
    // This ensures we use the same database userId for OpenFGA role assignments
    return userService.getOrCreateUser(email, name, firebaseUid)
        .flatMap(user -> {
          String dbUserId = user.getUserId();
          log.info("Onboarding user lookup: firebaseUid={}, dbUserId={}, email={}",
              firebaseUid, dbUserId, email);

          // Validate: User cannot be part of any tenant already
          return openFgaService.getUserTenants(dbUserId)
              .flatMap(existingTenants -> {
                if (existingTenants != null && !existingTenants.isEmpty()) {
                  // User is already part of one or more tenants - block onboarding
                  log.warn("Onboarding blocked: User is already part of {} organization(s): dbUserId={}",
                      existingTenants.size(), dbUserId);
                  return Single.error(new IllegalStateException(
                      "User is already part of an organization. You cannot onboard if you're already " +
                      "associated with any organization. Please use an existing organization or contact support."));
                }
                // No existing tenants - proceed with database userId
                return proceedWithOnboarding(dbUserId, email, name, organizationName, projectName, projectDescription);
              });
        })
        .doOnError(error ->
            log.error("Onboarding failed: firebaseUid={}, email={}", firebaseUid, email, error)
        );
  }

  /**
   * Proceed with actual tenant and project creation by delegating to the shared
   * {@code TenantService.createTenantWithProject} method, which is identical to the admin flow.
   */
  private Single<OnboardingResult> proceedWithOnboarding(
      String userId,
      String email,
      String name,
      String organizationName,
      String projectName,
      String projectDescription) {

    log.info("Creating new organization: userId={}, name={}", userId, organizationName);

    ReqUserInfo userInfo = ReqUserInfo.builder()
        .userId(userId)
        .email(email)
        .name(name)
        .build();

    return tenantService.createTenantWithProject(
            userInfo, organizationName, projectName, "Organization created during onboarding", projectDescription)
        .flatMap(tenantWithProject -> {
          String tenantId = tenantWithProject.getTenantId();
          return tenantService.getTenant(tenantId)
              .switchIfEmpty(io.reactivex.rxjava3.core.Maybe.error(
                  new RuntimeException("Tenant not found after creation: " + tenantId)))
              .toSingle()
              .flatMap(tenant ->
                  tierService.getTierNameById(tenant.getTierId())
                      .defaultIfEmpty("free")
                      .map(tierName -> {
                        String accessToken =
                            jwtService.generateAccessToken(userId, email, name, tenantId);
                        String refreshToken =
                            jwtService.generateRefreshToken(userId, email, name, tenantId);

                        log.info("Onboarding completed: userId={}, tenantId={}, projectId={}, tier={}",
                            userId, tenantId, tenantWithProject.getProjectId(), tierName);

                        return OnboardingResult.builder()
                            .userId(userId)
                            .email(email)
                            .name(name)
                            .tenantId(tenantId)
                            .tenantName(organizationName)
                            .tenantRole("admin")
                            .tier(tierName)
                            .projectId(tenantWithProject.getProjectId())
                            .projectName(projectName)
                            .projectApiKey(tenantWithProject.getRawApiKey())
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .tokenType("Bearer")
                            .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
                            .redirectTo("/projects/" + tenantWithProject.getProjectId())
                            .build();
                      })
              );
        })
        .doOnError(error ->
            log.error("Onboarding failed: userId={}", userId, error)
        );
  }

  /**
   * Result of onboarding operation.
   */
  @lombok.Data
  @lombok.Builder
  @lombok.NoArgsConstructor
  @lombok.AllArgsConstructor
  public static class OnboardingResult {
    private String userId;
    private String email;
    private String name;
    private String tenantId;
    private String tenantName;
    private String tenantRole;
    private String tier;
    private String projectId;
    private String projectName;
    private String projectApiKey;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Integer expiresIn;
    private String redirectTo;
  }
}
