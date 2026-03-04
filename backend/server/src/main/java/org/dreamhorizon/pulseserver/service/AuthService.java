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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.userdao.UserDao;
import org.dreamhorizon.pulseserver.dto.request.GetAccessTokenFromRefreshTokenRequestDto;
import org.dreamhorizon.pulseserver.model.LoginStatus;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthenticateResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.GetAccessTokenFromRefreshTokenResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.LoginResponse;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.VerifyAuthTokenResponseDto;
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
   * Uses OpenFGA as single source of truth for user-project relationships.
   * Activates pending users on first login.
   *
   * @param firebaseIdToken Firebase ID token from Google sign-in
   * @return LoginResponse with tokens (if has projects) or needs onboarding flag
   */
  public Single<LoginResponse> login(String firebaseIdToken) {
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
            // Query OpenFGA for user's projects
            openFgaService.getUserProjects(user.getUserId())
                .flatMap(projectIds -> {
                  if (projectIds == null || projectIds.isEmpty()) {
                    // No projects - user needs onboarding
                    log.info("User has no projects, requires onboarding: userId={}", user.getUserId());
                    return Single.just(LoginResponse.builder()
                        .status(LoginStatus.NEEDS_ONBOARDING)
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .needsOnboarding(true)
                        .build());
                  }

                  // User has projects - get first project's tenant
                  String firstProjectId = projectIds.get(0);
                  log.info("User has {} project(s), using first: userId={}, projectId={}",
                      projectIds.size(), user.getUserId(), firstProjectId);

                  return projectService.getProjectById(firstProjectId)
                      .flatMap(project -> {
                        String tenantId = project.getTenantId();

                        // Fetch tenant to get tier ID
                        return tenantDao.getTenantById(tenantId)
                            .switchIfEmpty(Single.error(new RuntimeException("Tenant not found: " + tenantId)))
                            .flatMap(tenant -> {
                              // Get tier name from tier ID
                              return tierService.getTierNameById(tenant.getTierId())
                                  .defaultIfEmpty("free")  // Fallback if tier not found
                                  .flatMap(tierName ->
                                      // Get user's tenant role
                                      openFgaService.getUserTenantRole(user.getUserId(), tenantId)
                                          .map(roleOpt -> {
                                            String tenantRole = roleOpt.orElse("member");

                                            // Generate JWT tokens with tenantId
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
                })
        )
        .doOnError(error ->
            log.error("Login failed: {}", error.getMessage(), error)
        );
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
            // User exists in DB
            // Check if pending
            if ("pending".equals(user.getStatus())) {
                log.info("Activating pending dev user on first login: userId={}", user.getUserId());
                return userService.activateUser(user.getUserId(), user.getUserId() + "-firebase-uid", name)
                    .andThen(openFgaService.getUserProjects(user.getUserId()))
                    .flatMap(projectIds -> proceedWithDevLogin(user.getUserId(), email, name, projectIds));
            } else {
                // Active user - normal flow
                userService.updateLastLogin(user.getUserId()).subscribe();
                return openFgaService.getUserProjects(user.getUserId())
                    .flatMap(projectIds -> proceedWithDevLogin(user.getUserId(), email, name, projectIds));
            }
        })
        .switchIfEmpty(Single.defer(() -> {
            // User doesn't exist in DB - check OpenFGA for pre-assigned projects
            log.info("Dev user not found in DB, checking OpenFGA: userId={}", userId);
            return openFgaService.getUserProjects(userId)
                .flatMap(projectIds -> {
                    if (projectIds == null || projectIds.isEmpty()) {
                        // No projects - needs onboarding
                        return Single.just(LoginResponse.builder()
                            .status(LoginStatus.NEEDS_ONBOARDING)
                            .userId(userId)
                            .email(email)
                            .name(name)
                            .needsOnboarding(true)
                            .build());
                    } else {
                        // Has projects but no DB record (created by admin)
                        // This shouldn't happen in normal flow but handle it
                        log.warn("User has OpenFGA projects but no DB record: {}", userId);
                        return proceedWithDevLogin(userId, email, name, projectIds);
                    }
                });
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

        boolean isValid = jwtService.isAccessToken(token);

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

    return Single.fromCallable(() -> {
      try {
        String refreshToken = request.getRefreshToken();

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
          throw new IllegalArgumentException("Refresh token is required");
        }

        if (!jwtService.isRefreshToken(refreshToken)) {
          log.error("Invalid token type. Expected refresh token.");
          throw new IllegalArgumentException("Invalid token type. Expected refresh token.");
        }


        Claims claims = jwtService.verifyToken(refreshToken);
        String userId = claims.getSubject();
        String email = claims.get(CLAIM_EMAIL, String.class);
        String name = claims.get(CLAIM_NAME, String.class);
        String tenantId = claims.get(CLAIM_TENANT_ID, String.class);

        String newAccessToken = jwtService.generateAccessToken(userId, email, name, tenantId);

        log.info("Successfully refreshed access token for user: {}", userId);

        return GetAccessTokenFromRefreshTokenResponseDto.builder()
            .accessToken(newAccessToken)
            .refreshToken(refreshToken)
            .tokenType(TOKEN_TYPE_BEARER)
            .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
            .build();

      } catch (Exception e) {
        log.error("Error refreshing access token: {}", e.getMessage());
        throw new RuntimeException("Failed to refresh access token", e);
      }
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
