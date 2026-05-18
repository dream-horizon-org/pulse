package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Claims;
import io.reactivex.rxjava3.core.Single;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dto.request.GetAccessTokenFromRefreshTokenRequestDto;
import org.dreamhorizon.pulseserver.model.LoginStatus;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthenticateResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.GetAccessTokenFromRefreshTokenResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.LoginResponse;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.VerifyAuthTokenResponseDto;
import org.dreamhorizon.pulseserver.service.auth.LoginHostContext;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.dreamhorizon.pulseserver.util.JwtUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AuthService {

  private static final String FIREBASE_JWKS_URL =
      "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

  private final ApplicationConfig applicationConfig;
  private final JwtService jwtService;
  private final TenantDao tenantDao;
  private final UserService userService;
  private final OpenFgaService openFgaService;
  private final ProjectService projectService;
  private final TierService tierService;
  private volatile String firebaseJwksCache;
  private volatile long firebaseJwksCacheExpiryMillis;
  private static final long JWKS_CACHE_TTL_MS = 3600_000L;

  // Development mode constants
  private static final String DEV_TENANT_ID = "default";
  private static final String DEV_USER_ID = "dev-user";
  private static final String DEV_EMAIL = "dev-user@localhost.local";
  private static final String DEV_NAME = "Development User";
  private static final String DEV_FIRST_NAME = "Development";
  private static final String DEV_LAST_NAME = "User";
  private static final String DEV_PROFILE_PICTURE = "";

  // Response constants
  private static final String TOKEN_TYPE_BEARER = "Bearer";

  // Claim keys (matching JwtService)
  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_NAME = "name";
  private static final String CLAIM_TENANT_ID = "tenantId";

  // System role constants
  private static final String SUPERADMIN_ROLE = "superadmin";
  private static final String INTERNAL_VIEWER_ROLE = "internal_viewer";

  public boolean isGoogleSignInEnabled() {
    // Check explicit environment variable first
    Boolean oauthEnabled = applicationConfig.getGoogleOAuthEnabled();
    if (oauthEnabled != null) {
      return oauthEnabled;
    }

    // Fallback: if client ID is not set, disable Google OAuth
    String clientId = applicationConfig.getGoogleOAuthClientId();
    if (clientId == null || clientId.trim().isEmpty()) {
      log.info("Google OAuth is disabled: client ID is not configured");
      return false;
    }

    // Default to enabled if client ID is present and no explicit flag is set
    return true;
  }

  private AuthenticateResponseDto createDevelopmentUser() {
    String accessToken = jwtService.generateAccessToken(DEV_USER_ID, DEV_EMAIL, DEV_NAME, DEV_TENANT_ID);
    String refreshToken = jwtService.generateRefreshToken(DEV_USER_ID, DEV_EMAIL, DEV_NAME, DEV_TENANT_ID);
    String idToken = jwtService.generateIdToken(DEV_USER_ID, DEV_EMAIL, DEV_FIRST_NAME, DEV_LAST_NAME, DEV_PROFILE_PICTURE);

    return AuthenticateResponseDto.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .idToken(idToken)
        .tokenType(TOKEN_TYPE_BEARER)
        .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
        .build();
  }

  /**
   * Simplified login flow without Firebase tenant claims.
   * Delegates to the overload with an empty host context.
   *
   * @param firebaseIdToken Firebase ID token from Google sign-in
   * @return LoginResponse with tokens or onboarding directive
   */
  public Single<LoginResponse> login(String firebaseIdToken) {
    return login(firebaseIdToken, LoginHostContext.empty());
  }

  /**
   * Login flow with host context for system-role workspace resolution.
   * Checks for superadmin / internal_viewer before falling through
   * to the normal customer tenant-resolution path.
   *
   * @param firebaseIdToken Firebase ID token from Google sign-in
   * @param hostContext     browser host for workspace tenant resolution
   * @return LoginResponse with tokens when user has a tenant (with or without projects),
   *     or needs onboarding when user has no tenant
   */
  public Single<LoginResponse> login(String firebaseIdToken, LoginHostContext hostContext) {
    // Development mode bypass - allow mock tokens
    if (!isGoogleSignInEnabled() || isMockToken(firebaseIdToken)) {
      log.info("Using development mode for login");
      return createDevelopmentLoginResponse(firebaseIdToken);
    }

    if (!isFirebaseConfigured()) {
      return Single.error(new IllegalArgumentException(
          "Firebase is not configured. Set CONFIG_SERVICE_APPLICATION_FIREBASEPROJECTID."));
    }

    return verifySimpleFirebaseToken(firebaseIdToken)
        .flatMap(userInfo -> {
            // Check if user exists by email
            return userService.getUserByEmail(userInfo.email)
                .switchIfEmpty(Single.defer(() -> {
                    // New user - create with Firebase UID and needs onboarding
                    return userService.getOrCreateUser(userInfo.email, userInfo.name, userInfo.userId);
                }))
                .flatMap(user -> {
                    // Check if user is pending (added by admin but never logged in)
                    if ("pending".equals(user.getStatus())) {
                        log.info("Activating pending user on first login: userId={}, email={}",
                            user.getUserId(), user.getEmail());

                        // Activate the user and update Firebase UID
                        return userService.activateUser(
                            user.getUserId(),
                            userInfo.userId,  // Firebase UID
                            userInfo.name
                        ).andThen(Single.just(user.toBuilder()
                            .status("active")
                            .firebaseUid(userInfo.userId)
                            .name(userInfo.name)
                            .build()));
                    } else {
                        // Already active user - just update last login
                        userService.updateLastLogin(user.getUserId()).subscribe();
                        return Single.just(user);
                    }
                });
        })
        .flatMap(user ->
            // System role check: fire both FGA queries concurrently (zipper must not return null)
            Single.zip(
                openFgaService.isSuperAdmin(user.getUserId()),
                openFgaService.isInternalViewer(user.getUserId()),
                (isSa, isIv) -> {
                  if (Boolean.TRUE.equals(isSa)) {
                    return Optional.of("superadmin");
                  }
                  if (Boolean.TRUE.equals(isIv)) {
                    return Optional.of("internal_viewer");
                  }
                  return Optional.<String>empty();
                })
            .flatMap(systemRoleOpt -> {
              if (systemRoleOpt.isPresent()) {
                String systemRole = systemRoleOpt.get();
                log.info("System role detected: userId={}, systemRole={}", user.getUserId(), systemRole);
                return buildSystemRoleLoginSuccess(user, systemRole, hostContext);
              }
              return customerLoginFlow(user);
            })
        )
        .doOnError(error ->
            log.error("Login failed: {}", error.getMessage(), error)
        );
  }

  /**
   * Standard customer login flow: resolve tenants/projects from OpenFGA.
   */
  private Single<LoginResponse> customerLoginFlow(
      org.dreamhorizon.pulseserver.model.User user) {
    return openFgaService.getUserTenants(user.getUserId())
        .flatMap(tenantIds -> {
          if (tenantIds == null || tenantIds.isEmpty()) {
            log.info("User has no tenants, requires onboarding: userId={}", user.getUserId());
            return Single.just(LoginResponse.builder()
                .status(LoginStatus.NEEDS_ONBOARDING)
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .needsOnboarding(true)
                .build());
          }
          return openFgaService.getUserProjects(user.getUserId())
              .flatMap(projectIds -> {
                if (projectIds == null || projectIds.isEmpty()) {
                  log.info("User has tenant(s) but no projects, tenant-only login: userId={}, tenantId={}",
                      user.getUserId(), tenantIds.get(0));
                  return buildLoginSuccessWithTenantOnly(
                      user.getUserId(), user.getEmail(), user.getName(), tenantIds.get(0));
                }

                String firstProjectId = projectIds.get(0);
                log.info("User has {} project(s), using first: userId={}, projectId={}",
                    projectIds.size(), user.getUserId(), firstProjectId);

                return projectService.getProjectById(firstProjectId)
                    .flatMap(project -> {
                      String tenantId = project.getTenantId();

                      return tenantDao.getTenantById(tenantId)
                          .switchIfEmpty(Single.error(new RuntimeException("Tenant not found: " + tenantId)))
                          .flatMap(tenant -> {
                            return tierService.getTierNameById(tenant.getTierId())
                                .defaultIfEmpty("free")
                                .flatMap(tierName ->
                                    openFgaService.getUserTenantRole(user.getUserId(), tenantId)
                                        .map(roleOpt -> {
                                          String tenantRole = roleOpt.orElse("member");

                                          String accessToken = jwtService.generateAccessToken(
                                              user.getUserId(), user.getEmail(), user.getName(), tenantId);
                                          String refreshToken = jwtService.generateRefreshToken(
                                              user.getUserId(), user.getEmail(), user.getName(), tenantId);

                                          log.info("Login successful: userId={}, tenantId={}, tenantRole={}, tier={}",
                                              user.getUserId(), tenantId, tenantRole, tierName);

                                          return LoginResponse.builder()
                                              .status(LoginStatus.SUCCESS)
                                              .accessToken(accessToken)
                                              .refreshToken(refreshToken)
                                              .userId(user.getUserId())
                                              .email(user.getEmail())
                                              .name(user.getName())
                                              .tenantId(tenantId)
                                              .tenantName(tenant.getName())
                                              .tenantRole(tenantRole)
                                              .tier(tierName)
                                              .needsOnboarding(false)
                                              .tokenType(TOKEN_TYPE_BEARER)
                                              .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
                                              .build();
                                        })
                                );
                          });
                    });
              });
        });
  }

  /**
   * Public method to verify Firebase token for onboarding flow.
   * This is used during onboarding when the user doesn't yet have a tenant/project.
   */
  public Single<UserInfo> verifyFirebaseTokenForOnboarding(String idTokenString) {
    return verifySimpleFirebaseToken(idTokenString);
  }

  /**
   * Simple Firebase token verification without tenant claim checking.
   * Only verifies signature, issuer, audience, and expiration.
   */
  private Single<UserInfo> verifySimpleFirebaseToken(String idTokenString) {
    String projectId = applicationConfig.getFirebaseProjectId().trim();
    String expectedIssuer = "https://securetoken.google.com/" + projectId;

    return Single.fromCallable(() -> {
      try {
        SignedJWT signedJWT = SignedJWT.parse(idTokenString);
        String kid = signedJWT.getHeader().getKeyID();

        if (kid == null) {
          throw new IllegalArgumentException("Invalid Firebase token: missing key ID");
        }

        // Fetch and verify with Firebase public keys
        String jwksJson = fetchFirebaseJwks();
        JWKSet jwkSet = JWKSet.parse(jwksJson);
        JWK jwk = jwkSet.getKeys().stream()
            .filter(k -> kid.equals(k.getKeyID()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No matching key found for token"));

        if (!(jwk instanceof RSAKey rsaKey)) {
          throw new IllegalArgumentException("Invalid key type");
        }

        JWSVerifier verifier = new RSASSAVerifier(rsaKey);
        if (!signedJWT.verify(verifier)) {
          throw new IllegalArgumentException("Token signature verification failed");
        }

        var claims = signedJWT.getJWTClaimsSet();

        // Validate issuer
        if (!expectedIssuer.equals(claims.getIssuer())) {
          throw new IllegalArgumentException("Invalid token issuer");
        }

        // Validate audience
        var audience = claims.getAudience();
        if (audience == null || !audience.contains(projectId)) {
          throw new IllegalArgumentException("Invalid token audience");
        }

        // Validate expiration
        Date exp = claims.getExpirationTime();
        if (exp == null || exp.before(new Date())) {
          throw new IllegalArgumentException("Token has expired");
        }

        // Extract user info
        String userId = claims.getSubject();
        String email = claims.getStringClaim("email");
        String name = claims.getStringClaim("name");
        String picture = claims.getStringClaim("picture");

        if (userId == null || userId.isBlank()) {
          throw new IllegalArgumentException("Token missing user ID");
        }

        if (email == null || email.isBlank()) {
          throw new IllegalArgumentException(
              "Firebase token is missing email claim. Please ensure your authentication includes email permissions.");
        }

        return new UserInfo(userId, email, name, picture);

      } catch (Exception e) {
        log.error("Firebase token verification failed: {}", e.getMessage());
        throw new IllegalArgumentException("Invalid Firebase token: " + e.getMessage(), e);
      }
    });
  }

  /**
   * Helper class for user information from Firebase token.
   * Made public for use in onboarding flow.
   */
  public static class UserInfo {
    public final String userId;
    public final String email;
    public final String name;
    public final String profilePicture;

    public UserInfo(String userId, String email, String name, String profilePicture) {
      this.userId = userId;
      this.email = email != null ? email : "";
      this.name = name != null ? name : "";
      this.profilePicture = profilePicture != null ? profilePicture : "";
    }
  }

  /**
   * Check if token is a mock/development token.
   */
  private boolean isMockToken(String token) {
    if (token == null) {
      return false;
    }
    // Accept various mock token formats for development
    return token.startsWith("mock-")
        || token.startsWith("dev-")
        || token.equals("test-token-user1")
        || token.equals("test-token-user2")
        || !token.contains(".");  // Not a JWT format
  }

  /**
   * Builds a successful login response using tenant context only (no project).
   * Used when the user belongs to a tenant but has no project assignments.
   */
  private Single<LoginResponse> buildLoginSuccessWithTenantOnly(String userId, String email, String name,
      String tenantId) {
    return tenantDao.getTenantById(tenantId)
        .switchIfEmpty(Single.error(new RuntimeException("Tenant not found: " + tenantId)))
        .flatMap(tenant ->
            tierService.getTierNameById(tenant.getTierId())
                .defaultIfEmpty("free")
                .flatMap(tierName ->
                    openFgaService.getUserTenantRole(userId, tenantId)
                        .map(roleOpt -> {
                          String tenantRole = roleOpt.orElse("member");
                          String accessToken = jwtService.generateAccessToken(userId, email, name, tenantId);
                          String refreshToken = jwtService.generateRefreshToken(userId, email, name, tenantId);

                          log.info("Login successful (tenant-only): userId={}, tenantId={}, tenantRole={}, tier={}",
                              userId, tenantId, tenantRole, tierName);

                          return LoginResponse.builder()
                              .status(LoginStatus.SUCCESS)
                              .accessToken(accessToken)
                              .refreshToken(refreshToken)
                              .userId(userId)
                              .email(email)
                              .name(name)
                              .tenantId(tenantId)
                              .tenantName(tenant.getName())
                              .tenantRole(tenantRole)
                              .tier(tierName)
                              .needsOnboarding(false)
                              .tokenType(TOKEN_TYPE_BEARER)
                              .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
                              .build();
                        })
                )
        );
  }

  /**
   * Builds a login response for system-role users (superadmin / internal_viewer).
   * Resolves workspace tenant from host context; never triggers NEEDS_ONBOARDING.
   */
  private Single<LoginResponse> buildSystemRoleLoginSuccess(
      org.dreamhorizon.pulseserver.model.User user, String systemRole, LoginHostContext hostContext) {

    return resolveWorkspaceTenant(hostContext)
        .flatMap(workspaceTenantIdOpt -> {
            String workspaceTenantId = workspaceTenantIdOpt.orElse(null);

            Single<Optional<Tenant>> tenantDetailSingle = workspaceTenantId != null
                ? tenantDao.getTenantById(workspaceTenantId)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty())
                : Single.just(Optional.empty());

            return tenantDetailSingle.flatMap(tenantOpt -> {
                String tenantName = tenantOpt.map(Tenant::getName).orElse(null);

                Single<String> tierSingle = tenantOpt
                    .<Single<String>>map(t -> tierService.getTierNameById(t.getTierId()).defaultIfEmpty("free"))
                    .orElse(Single.just("free"));

                return tierSingle.map(tier -> {
                    String accessToken = jwtService.generateAccessToken(
                        user.getUserId(), user.getEmail(), user.getName(),
                        workspaceTenantId, systemRole);

                    String refreshToken = jwtService.generateRefreshToken(
                        user.getUserId(), user.getEmail(), user.getName(),
                        workspaceTenantId);

                    log.info("System-role login successful: userId={}, systemRole={}, tenantId={}",
                        user.getUserId(), systemRole, workspaceTenantId);

                    return LoginResponse.builder()
                        .status(LoginStatus.SUCCESS)
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .tokenType(TOKEN_TYPE_BEARER)
                        .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .tenantId(workspaceTenantId)
                        .tenantName(tenantName)
                        .tenantRole(null)
                        .tier(tier)
                        .systemRole(systemRole)
                        .needsOnboarding(false)
                        .build();
                });
            });
        });
  }

  /**
   * Resolves a workspace tenantId from the host context.
   * Priority: pulseClientHost then X-Forwarded-Host.
   * Returns Optional.empty() if no host or no matching tenant found.
   */
  private Single<Optional<String>> resolveWorkspaceTenant(LoginHostContext ctx) {
    String rawHost = ctx.getPulseClientHost();
    if (rawHost == null || rawHost.isBlank()) {
      rawHost = ctx.getForwardedHost();
    }
    if (rawHost == null || rawHost.isBlank()) {
      return Single.just(Optional.empty());
    }
    String normalised = rawHost.toLowerCase().replaceAll(":\\d+$", "").trim();
    return tenantDao.getTenantByDomainName(normalised)
        .map(tenant -> Optional.of(tenant.getTenantId()))
        .defaultIfEmpty(Optional.empty());
  }

  /**
   * Dev login after DB user is resolved (or mock userId): system-role check, then tenants/projects.
   */
  private Single<LoginResponse> devLoginAfterTenantsResolved(String userId, String email, String name) {
    return Single.zip(
            openFgaService.isSuperAdmin(userId),
            openFgaService.isInternalViewer(userId),
            (sa, iv) -> {
              if (Boolean.TRUE.equals(sa)) {
                return Optional.of("superadmin");
              }
              if (Boolean.TRUE.equals(iv)) {
                return Optional.of("internal_viewer");
              }
              return Optional.<String>empty();
            })
        .flatMap(roleOpt -> {
          if (roleOpt.isPresent()) {
            org.dreamhorizon.pulseserver.model.User syntheticUser =
                org.dreamhorizon.pulseserver.model.User.builder()
                    .userId(userId).email(email).name(name).build();
            return buildSystemRoleLoginSuccess(syntheticUser, roleOpt.get(), LoginHostContext.empty());
          }
          return devCustomerLoginFlow(userId, email, name);
        });
  }

  /**
   * Original dev customer login flow (post system-role check).
   */
  private Single<LoginResponse> devCustomerLoginFlow(String userId, String email, String name) {
    return openFgaService.getUserTenants(userId)
        .flatMap(tenantIds -> {
          if (tenantIds == null || tenantIds.isEmpty()) {
            log.info("Dev user has no tenants, requires onboarding: userId={}", userId);
            return Single.just(LoginResponse.builder()
                .status(LoginStatus.NEEDS_ONBOARDING)
                .userId(userId)
                .email(email)
                .name(name)
                .needsOnboarding(true)
                .build());
          }
          return openFgaService.getUserProjects(userId)
              .flatMap(projectIds -> {
                if (projectIds == null || projectIds.isEmpty()) {
                  return buildLoginSuccessWithTenantOnly(userId, email, name, tenantIds.get(0));
                }
                return proceedWithDevLogin(userId, email, name, projectIds);
              });
        });
  }

  /**
   * Create development login response with proper user flow.
   * Extracts user info from mock token and follows normal login flow.
   * Handles pending user activation.
   */
  private Single<LoginResponse> createDevelopmentLoginResponse(String mockToken) {
    // Extract user info from mock token
    String userId;
    String email;
    String name;

    // Parse mock token to determine which user
    if (mockToken != null && (mockToken.contains("user2") || mockToken.contains("2"))) {
      userId = "mock-user-2";
      email = "user2@example.com";
      name = "Test User 2";
    } else {
      userId = "mock-user-1";
      email = "user1@example.com";
      name = "Test User 1";
    }

    log.info("Development mode login: userId={}, email={}", userId, email);

    // Check if user exists in database
    return userService.getUserByEmail(email)
        .flatMapSingle(user -> {
            if ("pending".equals(user.getStatus())) {
                log.info("Activating pending dev user on first login: userId={}", user.getUserId());
                return userService.activateUser(user.getUserId(), user.getUserId() + "-firebase-uid", name)
                    .andThen(devLoginAfterTenantsResolved(user.getUserId(), email, name));
            }
            userService.updateLastLogin(user.getUserId()).subscribe();
            return devLoginAfterTenantsResolved(user.getUserId(), email, name);
        })
        .switchIfEmpty(Single.defer(() -> {
            log.info("Dev user not found in DB, checking OpenFGA: userId={}", userId);
            return devLoginAfterTenantsResolved(userId, email, name);
        }))
        .doOnError(error ->
            log.error("Dev login failed: {}", error.getMessage(), error)
        );
  }

  /**
   * Helper to proceed with dev login after activation/verification
   */
  private Single<LoginResponse> proceedWithDevLogin(String userId, String email, String name,
                                                    java.util.List<String> projectIds) {
    if (projectIds == null || projectIds.isEmpty()) {
        log.info("Dev user has no projects, requires onboarding: userId={}", userId);
        return Single.just(LoginResponse.builder()
            .status(LoginStatus.NEEDS_ONBOARDING)
            .userId(userId)
            .email(email)
            .name(name)
            .needsOnboarding(true)
            .build());
    }

    // User has projects - get first project's tenant
    String firstProjectId = projectIds.get(0);
    log.info("Dev user has {} project(s), using first: userId={}, projectId={}",
        projectIds.size(), userId, firstProjectId);

    return projectService.getProjectById(firstProjectId)
        .flatMap(project -> {
          String tenantId = project.getTenantId();

          // Get user's tenant role
          return openFgaService.getUserTenantRole(userId, tenantId)
              .map(roleOpt -> {
                String tenantRole = roleOpt.orElse("member");

                // Generate JWT tokens with tenantId
                String accessToken = jwtService.generateAccessToken(userId, email, name, tenantId);
                String refreshToken = jwtService.generateRefreshToken(userId, email, name, tenantId);

                log.info("Dev login successful: userId={}, tenantId={}, tenantRole={}",
                    userId, tenantId, tenantRole);

                    return LoginResponse.builder()
                        .status(LoginStatus.SUCCESS)
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .userId(userId)
                        .email(email)
                        .name(name)
                        .tenantId(tenantId)
                        .tenantRole(tenantRole)
                        .tier("free")  // TODO: Query from database
                        .needsOnboarding(false)
                        .tokenType(TOKEN_TYPE_BEARER)
                        .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
                        .build();
                });
        });
  }


  private boolean isFirebaseConfigured() {
    String projectId = applicationConfig.getFirebaseProjectId();
    return projectId != null && !projectId.trim().isEmpty();
  }

  private String fetchFirebaseJwks() throws IOException {
    long now = System.currentTimeMillis();
    if (firebaseJwksCache != null && now < firebaseJwksCacheExpiryMillis) {
      return firebaseJwksCache;
    }
    synchronized (this) {
      if (firebaseJwksCache != null && System.currentTimeMillis() < firebaseJwksCacheExpiryMillis) {
        return firebaseJwksCache;
      }
      var conn = new URL(FIREBASE_JWKS_URL).openConnection();
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(5000);
      try (var in = conn.getInputStream()) {
        firebaseJwksCache = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      firebaseJwksCacheExpiryMillis = System.currentTimeMillis() + JWKS_CACHE_TTL_MS;
      return firebaseJwksCache;
    }
  }


  private Single<AuthenticateResponseDto> verifyFirebaseIdToken(String idTokenString, String requestTenantId) {
    if (!isFirebaseConfigured()) {
      return Single.error(new IllegalArgumentException("Firebase is not configured. Set CONFIG_SERVICE_APPLICATION_FIREBASEPROJECTID."));
    }
    String projectId = applicationConfig.getFirebaseProjectId().trim();
    String expectedIssuer = "https://securetoken.google.com/" + projectId;
    try {
      SignedJWT signedJWT = SignedJWT.parse(idTokenString);
      String kid = signedJWT.getHeader().getKeyID();
      if (kid == null) {
        log.error("Firebase token missing kid");
        return Single.error(new IllegalArgumentException("Invalid Firebase token: missing key ID."));
      }
      String jwksJson = fetchFirebaseJwks();
      JWKSet jwkSet = JWKSet.parse(jwksJson);
      JWK jwk = jwkSet.getKeys().stream()
          .filter(k -> kid.equals(k.getKeyID()))
          .findFirst()
          .orElse(null);
      if (!(jwk instanceof RSAKey rsaKey)) {
        log.error("No RSA key found for kid: {}", kid);
        return Single.error(new IllegalArgumentException("Invalid Firebase token: unable to verify signature."));
      }
      JWSVerifier verifier = new RSASSAVerifier(rsaKey);
      if (!signedJWT.verify(verifier)) {
        log.error("Firebase token signature verification failed");
        return Single.error(new IllegalArgumentException("Invalid Firebase token: signature verification failed."));
      }
      var claims = signedJWT.getJWTClaimsSet();
      if (!expectedIssuer.equals(claims.getIssuer())) {
        log.error("Firebase token issuer mismatch: expected {} got {}", expectedIssuer, claims.getIssuer());
        return Single.error(
            new IllegalArgumentException("Invalid Firebase token: issuer mismatch. Check your Firebase project configuration."));
      }
      var audience = claims.getAudience();
      if (audience == null || !audience.contains(projectId)) {
        log.error("Firebase token audience mismatch: expected {} got {}", projectId, audience);
        return Single.error(
            new IllegalArgumentException("Invalid Firebase token: audience mismatch. Check your Firebase project configuration."));
      }
      Date exp = claims.getExpirationTime();
      if (exp == null || exp.before(new Date())) {
        log.error("Firebase token expired or missing exp");
        return Single.error(new IllegalArgumentException("Firebase token has expired. Please re-authenticate."));
      }

      String tokenTenant = getFirebaseTenantFromClaims(claims);
      if (tokenTenant == null || tokenTenant.isBlank()) {
        log.error("Firebase token missing tenant claim");
        return Single.error(new IllegalArgumentException("Firebase token missing tenant. Multi-tenant authentication requires a tenant."));
      }
      if (!tokenTenant.trim().equals(requestTenantId.trim())) {
        log.error("tenant-id header does not match token tenant: header={} token={}", requestTenantId, tokenTenant);
        return Single.error(new IllegalArgumentException("Tenant mismatch: tenant-id header does not match the token tenant."));
      }

      String userId = claims.getSubject();
      if (userId == null || userId.isBlank()) {
        log.error("Firebase token missing subject (user ID)");
        return Single.error(new IllegalArgumentException("Invalid Firebase token: missing user ID."));
      }
      String email = claims.getStringClaim("email");
      String name = claims.getStringClaim("name");
      final String finalEmail = email != null ? email : "";
      final String finalName = name != null ? name : "";

      // Look up tenant from database by gcpTenantId to get our internal tenantId (reactive)
      return tenantDao.getTenantByGcpTenantId(tokenTenant)
          .switchIfEmpty(Single.error(new IllegalArgumentException("Tenant not found. Please contact support.")))
          .flatMap(tenant -> {
            if (!Boolean.TRUE.equals(tenant.getIsActive())) {
              log.error("Tenant is not active: {}", tenant.getTenantId());
              return Single.error(new IllegalArgumentException("Tenant is not active. Please contact support."));
            }
            String tenantId = tenant.getTenantId();
            String accessToken = jwtService.generateAccessToken(userId, finalEmail, finalName, tenantId);
            String refreshToken = jwtService.generateRefreshToken(userId, finalEmail, finalName, tenantId);
            return Single.just(AuthenticateResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .idToken(idTokenString)
                .tokenType(TOKEN_TYPE_BEARER)
                .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
                .build());
          })
          .doOnError(error -> log.error("Tenant not found in database for gcpTenantId: {}", tokenTenant));
    } catch (Exception e) {
      log.error("Firebase ID token verification failed: {}", e.getMessage(), e);
      return Single.error(new IllegalArgumentException("Firebase token verification failed: " + e.getMessage(), e));
    }
  }

  private static String getFirebaseTenantFromClaims(com.nimbusds.jwt.JWTClaimsSet claims) {
    try {
      Object firebase = claims.getClaim("firebase");
      if (firebase instanceof java.util.Map) {
        @SuppressWarnings("unchecked")
        Object tenant = ((java.util.Map<String, Object>) firebase).get("tenant");
        return tenant != null ? tenant.toString() : null;
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  public Single<AuthenticateResponseDto> verifyGoogleIdToken(String idTokenString, String requestTenantId) {
    if (!isGoogleSignInEnabled()) {
      return Single.just(createDevelopmentUser());
    }

    if (StringUtils.isBlank(requestTenantId)) {
      return Single.error(new IllegalArgumentException(
          "tenant-id header is required for Firebase (multi-tenant) authentication."));
    }

    if (!JwtUtils.isFirebaseIssuer(JwtUtils.jwtIssuer(idTokenString))) {
      return Single.error(new IllegalArgumentException(
          "Only Firebase ID tokens are supported. Please authenticate using Firebase Authentication."));
    }

    return verifyFirebaseIdToken(idTokenString, requestTenantId);
  }

  public Single<VerifyAuthTokenResponseDto> verifyAuthToken(String authorization) {
    return Single.fromCallable(() -> {
      try {
        String token = extractTokenFromHeader(authorization);

        if (token == null || token.trim().isEmpty()) {
          log.warn("Empty or null token provided");
          return VerifyAuthTokenResponseDto.builder()
              .isAuthTokenValid(false)
              .build();
        }

        boolean isValid = jwtService.isAccessToken(token) && !jwtService.isTokenExpired(token);

        return VerifyAuthTokenResponseDto.builder()
            .isAuthTokenValid(isValid)
            .build();

      } catch (Exception e) {
        log.error("Error verifying token", e);
        return VerifyAuthTokenResponseDto.builder()
            .isAuthTokenValid(false)
            .build();
      }
    });
  }


  public Single<GetAccessTokenFromRefreshTokenResponseDto> getAccessTokenFromRefreshToken(
      GetAccessTokenFromRefreshTokenRequestDto request) {

    String refreshToken = request.getRefreshToken();

    if (refreshToken == null || refreshToken.trim().isEmpty()) {
      return Single.error(ServiceError.AUTHENTICATION_BAD_REQUEST
          .getCustomException("Refresh token is required"));
    }

    if (!jwtService.isRefreshToken(refreshToken)) {
      log.error("Invalid token type. Expected refresh token.");
      return Single.error(ServiceError.AUTHENTICATION_BAD_REQUEST
          .getCustomException("Invalid token type. Expected refresh token."));
    }

    if (jwtService.isTokenExpired(refreshToken)) {
      log.info("Refresh token has expired");
      return Single.error(ServiceError.UNAUTHORISED
          .getCustomException("Refresh token has expired. Please log in again."));
    }

    return Single.defer(() -> {
      Claims claims = jwtService.verifyToken(refreshToken);
      String userId = claims.getSubject();
      String email = claims.get(CLAIM_EMAIL, String.class);
      String name = claims.get(CLAIM_NAME, String.class);
      final String tokenTenantId = claims.get(CLAIM_TENANT_ID, String.class);
      final String requestedTenantId = request.getTenantId();

      return Single.zip(
          openFgaService.isSuperAdmin(userId),
          openFgaService.isInternalViewer(userId),
          (sa, iv) -> {
            if (Boolean.TRUE.equals(sa)) {
              return Optional.of(SUPERADMIN_ROLE);
            }
            if (Boolean.TRUE.equals(iv)) {
              return Optional.of(INTERNAL_VIEWER_ROLE);
            }
            return Optional.<String>empty();
          })
      .map(systemRoleOpt -> {
          String systemRole = systemRoleOpt.orElse(null);

          // System-role tenant switching: honour request-body tenantId only when the
          // live OpenFGA check confirms a system role. Regular users cannot use this
          // field to scope their JWT to a tenant they do not belong to.
          boolean isSystemRole = systemRole != null;
          String effectiveWorkspaceId = (isSystemRole
              && requestedTenantId != null
              && !requestedTenantId.isBlank())
              ? requestedTenantId : tokenTenantId;

          String newAccessToken = isSystemRole
              ? jwtService.generateAccessToken(userId, email, name, effectiveWorkspaceId, systemRole)
              : jwtService.generateAccessToken(userId, email, name, effectiveWorkspaceId);

          String newRefreshToken = jwtService.generateRefreshToken(userId, email, name, effectiveWorkspaceId);

          log.info("Successfully refreshed access token for user: {}, systemRole: {}", userId, systemRole);

          return GetAccessTokenFromRefreshTokenResponseDto.builder()
              .accessToken(newAccessToken)
              .refreshToken(newRefreshToken)
              .tokenType(TOKEN_TYPE_BEARER)
              .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
              .systemRole(systemRole)
              .build();
      });
    });
  }


  private String extractTokenFromHeader(String authorization) {
    if (authorization == null || authorization.trim().isEmpty()) {
      return null;
    }

    if (authorization.toLowerCase().startsWith("bearer ")) {
      return authorization.substring(7).trim();
    }

    return authorization.trim();
  }
}
