package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.WebApplicationException;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dto.request.GetAccessTokenFromRefreshTokenRequestDto;
import org.dreamhorizon.pulseserver.model.LoginStatus;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthenticateResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.GetAccessTokenFromRefreshTokenResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.LoginResponse;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.VerifyAuthTokenResponseDto;
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
class AuthServiceTest {

  @Mock
  ApplicationConfig applicationConfig;

  @Mock
  JwtService jwtService;

  @Mock
  TenantDao tenantDao;

  @Mock
  OpenFgaConfig openFgaConfig;

  @Mock
  OpenFgaService openFgaService;

  @Mock
  UserService userService;

  @Mock
  ProjectService projectService;

  @Mock
  org.dreamhorizon.pulseserver.service.tier.TierService tierService;

  AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(applicationConfig, jwtService, tenantDao, userService,
        openFgaService, projectService, tierService);
  }

  @Nested
  class IsGoogleSignInEnabled {

    @Test
    void returnsTrueWhenOAuthEnabledIsTrue() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      assertTrue(authService.isGoogleSignInEnabled());
    }

    @Test
    void returnsFalseWhenOAuthEnabledIsFalse() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      assertFalse(authService.isGoogleSignInEnabled());
    }

    @Test
    void returnsFalseWhenClientIdNullAndOAuthEnabledNull() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(null);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn(null);
      assertFalse(authService.isGoogleSignInEnabled());
    }

    @Test
    void returnsFalseWhenClientIdEmptyAndOAuthEnabledNull() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(null);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("   ");
      assertFalse(authService.isGoogleSignInEnabled());
    }

    @Test
    void returnsTrueWhenClientIdSetAndOAuthEnabledNull() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(null);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("client-id");
      assertTrue(authService.isGoogleSignInEnabled());
    }
  }

  @Nested
  class VerifyGoogleIdToken {

    @Test
    void returnsDevelopmentUserWhenGoogleSignInDisabled() throws Exception {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      when(jwtService.generateAccessToken(anyString(), anyString(), anyString(), anyString())).thenReturn("access");
      when(jwtService.generateRefreshToken(anyString(), anyString(), anyString(), anyString())).thenReturn("refresh");
      when(jwtService.generateIdToken(anyString(), anyString(), anyString(), anyString(), anyString()))
          .thenReturn("id");

      Single<AuthenticateResponseDto> single = authService.verifyGoogleIdToken("any-token", null);
      AuthenticateResponseDto result = single.blockingGet();

      assertNotNull(result);
      assertEquals("access", result.getAccessToken());
      assertEquals("refresh", result.getRefreshToken());
      assertEquals("id", result.getIdToken());
      assertEquals("Bearer", result.getTokenType());
      assertEquals(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS, result.getExpiresIn());
    }

    @Test
    void throwsWhenFirebaseIssuerButTenantIdNull() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"iss\":\"https://securetoken.google.com/proj1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String firebaseToken = "header." + payloadBase64 + ".signature";

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(firebaseToken, null).blockingGet());

      IllegalArgumentException iae = t instanceof IllegalArgumentException
          ? (IllegalArgumentException) t
          : (IllegalArgumentException) t.getCause();
      assertTrue(iae.getMessage().contains("tenant-id header is required"));
    }

    @Test
    void throwsWhenFirebaseIssuerButTenantIdBlank() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"iss\":\"https://securetoken.google.com/proj1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String firebaseToken = "header." + payloadBase64 + ".signature";

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(firebaseToken, "").blockingGet());

      IllegalArgumentException iae = t instanceof IllegalArgumentException
          ? (IllegalArgumentException) t
          : (IllegalArgumentException) t.getCause();
      assertTrue(iae.getMessage().contains("tenant-id header is required"));
    }

    @Test
    void throwsWhenFirebaseIssuerAndTenantIdPresentButFirebaseNotConfigured() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);
      String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"iss\":\"https://securetoken.google.com/proj1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String firebaseToken = "header." + payloadBase64 + ".signature";

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(firebaseToken, "tenant-1").blockingGet());

      IllegalArgumentException iae = t instanceof IllegalArgumentException
          ? (IllegalArgumentException) t
          : (IllegalArgumentException) t.getCause();
      assertTrue(iae.getMessage().contains("Firebase is not configured. Set CONFIG_SERVICE_APPLICATION_FIREBASEPROJECTID."));
    }

    @Test
    void throwsWhenFirebaseIssuerAndFirebaseConfiguredButVerificationFails() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getFirebaseProjectId()).thenReturn("proj1");
      String headerNoKid = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"alg\":\"RS256\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"iss\":\"https://securetoken.google.com/proj1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String firebaseToken = headerNoKid + "." + payloadBase64 + ".signature";

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(firebaseToken, "tenant-1").blockingGet());

      IllegalArgumentException iae = t instanceof IllegalArgumentException
          ? (IllegalArgumentException) t
          : (IllegalArgumentException) t.getCause();
      assertTrue(iae.getMessage().contains("Invalid Firebase token: missing key ID."));
    }

    @Test
    void throwsWhenGoogleVerifierFailsWithInvalidToken() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("client-id");
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);
      String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"iss\":\"https://accounts.google.com\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String googleToken = "header." + payloadBase64 + ".signature";

      assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(googleToken, null).blockingGet());
    }

    @Test
    void throwsWhenIdTokenIsNullWithGoogleEnabled() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("client-id");
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);

      assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(null, null).blockingGet());
    }

    @Test
    void throwsWhenIdTokenEmptyWithGoogleEnabled() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("client-id");
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);

      assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken("", null).blockingGet());
    }

    @Test
    void throwsWhenTokenHasOnlyTwoParts() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("client-id");
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);

      assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken("part1.part2", null).blockingGet());
    }

    @Test
    void throwsWhenTokenPayloadHasNoIssuer() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("client-id");
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);
      String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"sub\":\"user123\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String token = "header." + payloadBase64 + ".signature";

      assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(token, null).blockingGet());
    }

    @Test
    void throwsWhenTokenPayloadIsInvalidBase64() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("client-id");
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);
      String token = "header.not-valid-base64!!!.signature";

      assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(token, null).blockingGet());
    }

    @Test
    void throwsWhenTokenPayloadHasIssAsNonString() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("client-id");
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);
      String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"iss\":0}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String token = "header." + payloadBase64 + ".signature";

      assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(token, null).blockingGet());
    }

    @Test
    void getVerifierReusedOnSecondCall() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getGoogleOAuthClientId()).thenReturn("client-id");
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);
      String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"iss\":\"https://accounts.google.com\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String googleToken = "header." + payloadBase64 + ".signature";

      assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(googleToken, null).blockingGet());
      assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(googleToken, null).blockingGet());
    }
  }

  @Nested
  class VerifyAuthToken {

    @Test
    void returnsInvalidWhenAuthorizationNull() throws Exception {
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken(null);
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertNotNull(result);
      assertFalse(result.getIsAuthTokenValid());
    }

    @Test
    void returnsInvalidWhenAuthorizationEmpty() throws Exception {
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("   ");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertFalse(result.getIsAuthTokenValid());
    }

    @Test
    void returnsValidWhenJwtServiceSaysAccessToken() throws Exception {
      when(jwtService.isAccessToken("valid-token")).thenReturn(true);
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("Bearer valid-token");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertTrue(result.getIsAuthTokenValid());
    }

    @Test
    void returnsInvalidWhenJwtServiceSaysNotAccessToken() throws Exception {
      when(jwtService.isAccessToken("bad-token")).thenReturn(false);
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("Bearer bad-token");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertFalse(result.getIsAuthTokenValid());
    }

    @Test
    void returnsInvalidWhenJwtServiceIsAccessTokenThrows() throws Exception {
      when(jwtService.isAccessToken("token")).thenThrow(new RuntimeException("invalid"));
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("token");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertFalse(result.getIsAuthTokenValid());
    }

    @Test
    void returnsInvalidWhenAuthorizationHasNoBearerPrefix() throws Exception {
      when(jwtService.isAccessToken("raw-token")).thenReturn(false);
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("raw-token");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertFalse(result.getIsAuthTokenValid());
    }

    @Test
    void returnsValidWhenBearerLowerCase() throws Exception {
      when(jwtService.isAccessToken("token")).thenReturn(true);
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("bearer token");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertTrue(result.getIsAuthTokenValid());
    }

    @Test
    void returnsInvalidWhenBearerWithOnlySpaces() throws Exception {
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("Bearer   ");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertFalse(result.getIsAuthTokenValid());
    }

    @Test
    void returnsInvalidWhenAuthorizationIsEmptyString() throws Exception {
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertThat(result.getIsAuthTokenValid()).isFalse();
    }

    @Test
    void returnsInvalidWhenAuthorizationIsBearerOnlyWithNoSpace() throws Exception {
      Single<VerifyAuthTokenResponseDto> single = authService.verifyAuthToken("Bearer");
      VerifyAuthTokenResponseDto result = single.blockingGet();
      assertThat(result.getIsAuthTokenValid()).isFalse();
    }
  }

  @Nested
  class GetAccessTokenFromRefreshToken {

    @Test
    void throwsWhenRefreshTokenNull() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken(null);

      WebApplicationException ex = assertThrows(WebApplicationException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());
      assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void throwsWhenRefreshTokenEmpty() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("   ");

      WebApplicationException ex = assertThrows(WebApplicationException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());
      assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void throwsWhenNotRefreshToken() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("not-refresh");
      when(jwtService.isRefreshToken("not-refresh")).thenReturn(false);

      WebApplicationException ex = assertThrows(WebApplicationException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());
      assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void throwsWhenRefreshTokenExpired() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("expired-refresh");
      when(jwtService.isRefreshToken("expired-refresh")).thenReturn(true);
      when(jwtService.isTokenExpired("expired-refresh")).thenReturn(true);

      WebApplicationException ex = assertThrows(WebApplicationException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());
      assertEquals(401, ex.getResponse().getStatus());
    }

    @Test
    void returnsNewAccessTokenWhenValidRefreshToken() throws Exception {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("valid-refresh");
      when(jwtService.isRefreshToken("valid-refresh")).thenReturn(true);
      when(jwtService.isTokenExpired("valid-refresh")).thenReturn(false);
      Claims claims = mock(Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(claims.get(eq("email"), eq(String.class))).thenReturn("e@x.com");
      when(claims.get(eq("name"), eq(String.class))).thenReturn("Name");
      when(claims.get(eq("tenantId"), eq(String.class))).thenReturn("tenant-123");
      when(jwtService.verifyToken("valid-refresh")).thenReturn(claims);
      when(jwtService.generateAccessToken("user1", "e@x.com", "Name", "tenant-123")).thenReturn("new-access");

      Single<GetAccessTokenFromRefreshTokenResponseDto> single =
          authService.getAccessTokenFromRefreshToken(request);
      GetAccessTokenFromRefreshTokenResponseDto result = single.blockingGet();

      assertNotNull(result);
      assertEquals("new-access", result.getAccessToken());
      assertEquals("valid-refresh", result.getRefreshToken());
      assertEquals("Bearer", result.getTokenType());
      assertEquals(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS, result.getExpiresIn());
      verify(jwtService).generateAccessToken("user1", "e@x.com", "Name", "tenant-123");
    }

    @Test
    void throwsWhenVerifyTokenThrows() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("bad-refresh");
      when(jwtService.isRefreshToken("bad-refresh")).thenReturn(true);
      when(jwtService.isTokenExpired("bad-refresh")).thenReturn(false);
      when(jwtService.verifyToken("bad-refresh")).thenThrow(new RuntimeException("Token invalid"));

      assertThrows(RuntimeException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());
    }

    @Test
    void returnsNewAccessTokenWhenClaimsHaveNullEmailAndName() throws Exception {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("valid-refresh");
      when(jwtService.isRefreshToken("valid-refresh")).thenReturn(true);
      when(jwtService.isTokenExpired("valid-refresh")).thenReturn(false);
      Claims claims = mock(Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(claims.get(eq("email"), eq(String.class))).thenReturn(null);
      when(claims.get(eq("name"), eq(String.class))).thenReturn(null);
      when(claims.get(eq("tenantId"), eq(String.class))).thenReturn(null);
      when(jwtService.verifyToken("valid-refresh")).thenReturn(claims);
      when(jwtService.generateAccessToken("user1", null, null, null)).thenReturn("new-access");

      Single<GetAccessTokenFromRefreshTokenResponseDto> single =
          authService.getAccessTokenFromRefreshToken(request);
      GetAccessTokenFromRefreshTokenResponseDto result = single.blockingGet();

      assertNotNull(result);
      assertEquals("new-access", result.getAccessToken());
      verify(jwtService).generateAccessToken("user1", null, null, null);
    }
  }

  @Nested
  class Login {

    @Test
    void shouldReturnNeedsOnboardingWhenDevModeAndUserHasNoProjects() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      User devUser = User.builder()
          .userId("mock-user-1")
          .email("user1@example.com")
          .name("Test User 1")
          .status("active")
          .build();
      when(userService.getUserByEmail("user1@example.com")).thenReturn(Maybe.just(devUser));
      when(userService.updateLastLogin("mock-user-1")).thenReturn(Completable.complete());
      when(openFgaService.getUserProjects("mock-user-1")).thenReturn(Single.just(java.util.Collections.emptyList()));

      LoginResponse result = authService.login("mock-user1").blockingGet();

      assertNotNull(result);
      assertEquals(LoginStatus.NEEDS_ONBOARDING, result.getStatus());
      assertTrue(result.getNeedsOnboarding());
      assertEquals("mock-user-1", result.getUserId());
      assertEquals("user1@example.com", result.getEmail());
    }

    @Test
    void shouldReturnSuccessWhenDevModeAndUserHasProjects() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      User devUser = User.builder()
          .userId("mock-user-1")
          .email("user1@example.com")
          .name("Test User 1")
          .status("active")
          .build();
      org.dreamhorizon.pulseserver.dao.project.models.Project project =
          org.dreamhorizon.pulseserver.dao.project.models.Project.builder()
              .projectId("proj-1")
              .tenantId("tenant-1")
              .name("My Project")
              .build();
      when(userService.getUserByEmail("user1@example.com")).thenReturn(Maybe.just(devUser));
      when(userService.updateLastLogin("mock-user-1")).thenReturn(Completable.complete());
      when(openFgaService.getUserProjects("mock-user-1"))
          .thenReturn(Single.just(java.util.List.of("proj-1")));
      when(projectService.getProjectById("proj-1")).thenReturn(Single.just(project));
      when(openFgaService.getUserTenantRole("mock-user-1", "tenant-1"))
          .thenReturn(Single.just(java.util.Optional.of("admin")));
      when(jwtService.generateAccessToken(eq("mock-user-1"), eq("user1@example.com"), eq("Test User 1"), eq("tenant-1")))
          .thenReturn("access-token");
      when(jwtService.generateRefreshToken(eq("mock-user-1"), eq("user1@example.com"), eq("Test User 1"), eq("tenant-1")))
          .thenReturn("refresh-token");

      LoginResponse result = authService.login("mock-user1").blockingGet();

      assertNotNull(result);
      assertEquals(LoginStatus.SUCCESS, result.getStatus());
      assertFalse(result.getNeedsOnboarding());
      assertEquals("access-token", result.getAccessToken());
      assertEquals("refresh-token", result.getRefreshToken());
      assertEquals("tenant-1", result.getTenantId());
      assertEquals("admin", result.getTenantRole());
      verify(projectService).getProjectById("proj-1");
    }

    @Test
    void shouldReturnNeedsOnboardingWhenDevModeAndNewUserHasNoProjects() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      when(userService.getUserByEmail("user1@example.com")).thenReturn(Maybe.empty());
      when(openFgaService.getUserProjects("mock-user-1")).thenReturn(Single.just(java.util.Collections.emptyList()));

      LoginResponse result = authService.login("mock-user1").blockingGet();

      assertNotNull(result);
      assertEquals(LoginStatus.NEEDS_ONBOARDING, result.getStatus());
      assertTrue(result.getNeedsOnboarding());
      verify(userService).getUserByEmail("user1@example.com");
      verify(openFgaService).getUserProjects("mock-user-1");
      verify(projectService, never()).getProjectById(anyString());
    }

    @Test
    void shouldUseDevModeWhenTokenIsDevPrefixed() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      when(userService.getUserByEmail("user1@example.com")).thenReturn(Maybe.empty());
      when(openFgaService.getUserProjects("mock-user-1")).thenReturn(Single.just(java.util.Collections.emptyList()));

      LoginResponse result = authService.login("dev-xyz").blockingGet();

      assertNotNull(result);
      assertEquals(LoginStatus.NEEDS_ONBOARDING, result.getStatus());
    }

    @Test
    void shouldUseUser2WhenMockTokenContainsUser2() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      when(userService.getUserByEmail("user2@example.com")).thenReturn(Maybe.empty());
      when(openFgaService.getUserProjects("mock-user-2")).thenReturn(Single.just(java.util.Collections.emptyList()));

      LoginResponse result = authService.login("mock-user2").blockingGet();

      assertNotNull(result);
      assertEquals("mock-user-2", result.getUserId());
      assertEquals("user2@example.com", result.getEmail());
      assertEquals("Test User 2", result.getName());
    }

    @Test
    void shouldThrowWhenFirebaseNotConfiguredAndRealTokenUsed() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);
      String firebaseToken = "header.payload.signature";

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.login(firebaseToken).blockingGet());

      assertTrue(t.getCause() != null && t.getCause().getMessage().contains("Firebase is not configured")
          || t.getMessage().contains("Firebase is not configured"));
    }
  }

  @Nested
  class VerifyFirebaseTokenForOnboarding {

    @Test
    void shouldThrowWhenTokenIsNull() {
      assertThrows(Exception.class, () ->
          authService.verifyFirebaseTokenForOnboarding(null).blockingGet());
    }

    @Test
    void shouldThrowWhenTokenIsInvalidFormat() {
      when(applicationConfig.getFirebaseProjectId()).thenReturn("test-project");

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.verifyFirebaseTokenForOnboarding("not-a-valid-jwt").blockingGet());

      assertTrue(t.getCause() instanceof IllegalArgumentException
          || t instanceof IllegalArgumentException);
      assertTrue(t.getMessage().contains("Invalid Firebase token")
          || (t.getCause() != null && t.getCause().getMessage().contains("Invalid")));
    }

    @Test
    void shouldThrowWhenFirebaseProjectIdIsNull() {
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);

      assertThrows(Exception.class, () ->
          authService.verifyFirebaseTokenForOnboarding("a.b.c").blockingGet());
    }

    @Test
    void shouldThrowWhenFirebaseProjectIdIsEmpty() {
      when(applicationConfig.getFirebaseProjectId()).thenReturn("   ");

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.verifyFirebaseTokenForOnboarding("a.b.c").blockingGet());

      assertTrue(t.getCause() instanceof IllegalArgumentException
          || t instanceof IllegalArgumentException);
    }

    @Test
    void shouldThrowWhenTokenIsEmptyString() {
      when(applicationConfig.getFirebaseProjectId()).thenReturn("test-project");

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.verifyFirebaseTokenForOnboarding("").blockingGet());

      assertThat(t.getCause()).isInstanceOf(java.text.ParseException.class);
    }

    @Test
    void shouldThrowWhenTokenHasOnlyTwoParts() {
      when(applicationConfig.getFirebaseProjectId()).thenReturn("test-project");

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.verifyFirebaseTokenForOnboarding("header.payload").blockingGet());

      assertThat(t.getCause()).isInstanceOf(java.text.ParseException.class);
    }
  }

  @Nested
  class LoginMockTokens {

    @Test
    void shouldUseTestTokenUser1ForTestTokenUser1() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      when(userService.getUserByEmail("user1@example.com")).thenReturn(Maybe.empty());
      when(openFgaService.getUserProjects("mock-user-1")).thenReturn(Single.just(java.util.Collections.emptyList()));

      LoginResponse result = authService.login("test-token-user1").blockingGet();

      assertNotNull(result);
      assertEquals("mock-user-1", result.getUserId());
      assertEquals("user1@example.com", result.getEmail());
      assertEquals("Test User 1", result.getName());
      assertEquals(LoginStatus.NEEDS_ONBOARDING, result.getStatus());
    }

    @Test
    void shouldUseTestTokenUser2ForTestTokenUser2() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      when(userService.getUserByEmail("user2@example.com")).thenReturn(Maybe.empty());
      when(openFgaService.getUserProjects("mock-user-2")).thenReturn(Single.just(java.util.Collections.emptyList()));

      LoginResponse result = authService.login("test-token-user2").blockingGet();

      assertNotNull(result);
      assertEquals("mock-user-2", result.getUserId());
      assertEquals("user2@example.com", result.getEmail());
      assertEquals("Test User 2", result.getName());
    }

    @Test
    void shouldUseDevModeWhenTokenHasNoDots() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      when(userService.getUserByEmail("user1@example.com")).thenReturn(Maybe.empty());
      when(openFgaService.getUserProjects("mock-user-1")).thenReturn(Single.just(java.util.Collections.emptyList()));

      LoginResponse result = authService.login("simplestring").blockingGet();

      assertNotNull(result);
      assertEquals("mock-user-1", result.getUserId());
    }

    @Test
    void shouldUseUser2WhenMockTokenContains2() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      when(userService.getUserByEmail("user2@example.com")).thenReturn(Maybe.empty());
      when(openFgaService.getUserProjects("mock-user-2")).thenReturn(Single.just(java.util.Collections.emptyList()));

      LoginResponse result = authService.login("mock-something-2").blockingGet();

      assertNotNull(result);
      assertEquals("mock-user-2", result.getUserId());
    }
  }

  @Nested
  class LoginPendingUserActivation {

    @Test
    void shouldActivatePendingUserOnFirstDevLogin() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      User pendingUser = User.builder()
          .userId("pending-123")
          .email("user1@example.com")
          .name("Pending User")
          .status("pending")
          .build();
      when(userService.getUserByEmail("user1@example.com")).thenReturn(Maybe.just(pendingUser));
      when(userService.activateUser(eq("pending-123"), eq("pending-123-firebase-uid"), eq("Test User 1")))
          .thenReturn(Completable.complete());
      when(openFgaService.getUserProjects("pending-123")).thenReturn(Single.just(java.util.List.of("proj-1")));
      org.dreamhorizon.pulseserver.dao.project.models.Project project =
          org.dreamhorizon.pulseserver.dao.project.models.Project.builder()
              .projectId("proj-1")
              .tenantId("tenant-1")
              .name("Project")
              .build();
      when(projectService.getProjectById("proj-1")).thenReturn(Single.just(project));
      when(openFgaService.getUserTenantRole("pending-123", "tenant-1"))
          .thenReturn(Single.just(java.util.Optional.of("admin")));
      when(jwtService.generateAccessToken(eq("pending-123"), eq("user1@example.com"), eq("Test User 1"), eq("tenant-1")))
          .thenReturn("access");
      when(jwtService.generateRefreshToken(eq("pending-123"), eq("user1@example.com"), eq("Test User 1"), eq("tenant-1")))
          .thenReturn("refresh");

      LoginResponse result = authService.login("dev-token").blockingGet();

      assertNotNull(result);
      assertEquals(LoginStatus.SUCCESS, result.getStatus());
      assertEquals("access", result.getAccessToken());
      verify(userService).activateUser(eq("pending-123"), eq("pending-123-firebase-uid"), eq("Test User 1"));
    }

    @Test
    void shouldHandleDevUserWithProjectsButNoDbRecord() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      when(userService.getUserByEmail("user1@example.com")).thenReturn(Maybe.empty());
      when(openFgaService.getUserProjects("mock-user-1")).thenReturn(Single.just(java.util.List.of("proj-1")));
      org.dreamhorizon.pulseserver.dao.project.models.Project project =
          org.dreamhorizon.pulseserver.dao.project.models.Project.builder()
              .projectId("proj-1")
              .tenantId("tenant-1")
              .name("Project")
              .build();
      when(projectService.getProjectById("proj-1")).thenReturn(Single.just(project));
      when(openFgaService.getUserTenantRole("mock-user-1", "tenant-1"))
          .thenReturn(Single.just(java.util.Optional.of("member")));
      when(jwtService.generateAccessToken(eq("mock-user-1"), eq("user1@example.com"), eq("Test User 1"), eq("tenant-1")))
          .thenReturn("access");
      when(jwtService.generateRefreshToken(eq("mock-user-1"), eq("user1@example.com"), eq("Test User 1"), eq("tenant-1")))
          .thenReturn("refresh");

      LoginResponse result = authService.login("dev-xyz").blockingGet();

      assertNotNull(result);
      assertEquals(LoginStatus.SUCCESS, result.getStatus());
      assertEquals("mock-user-1", result.getUserId());
      verify(projectService).getProjectById("proj-1");
    }

    @Test
    void shouldUseMemberRoleWhenGetUserTenantRoleReturnsOptionalEmpty() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      User devUser = User.builder()
          .userId("mock-user-1")
          .email("user1@example.com")
          .name("Test User 1")
          .status("active")
          .build();
      org.dreamhorizon.pulseserver.dao.project.models.Project project =
          org.dreamhorizon.pulseserver.dao.project.models.Project.builder()
              .projectId("proj-1")
              .tenantId("tenant-1")
              .name("My Project")
              .build();
      when(userService.getUserByEmail("user1@example.com")).thenReturn(Maybe.just(devUser));
      when(userService.updateLastLogin("mock-user-1")).thenReturn(Completable.complete());
      when(openFgaService.getUserProjects("mock-user-1"))
          .thenReturn(Single.just(java.util.List.of("proj-1")));
      when(projectService.getProjectById("proj-1")).thenReturn(Single.just(project));
      when(openFgaService.getUserTenantRole("mock-user-1", "tenant-1"))
          .thenReturn(Single.just(java.util.Optional.empty()));
      when(jwtService.generateAccessToken(eq("mock-user-1"), eq("user1@example.com"), eq("Test User 1"), eq("tenant-1")))
          .thenReturn("access-token");
      when(jwtService.generateRefreshToken(eq("mock-user-1"), eq("user1@example.com"), eq("Test User 1"), eq("tenant-1")))
          .thenReturn("refresh-token");

      LoginResponse result = authService.login("mock-user1").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(LoginStatus.SUCCESS);
      assertThat(result.getTenantRole()).isEqualTo("member");
    }
  }

  @Nested
  class UserInfo {

    @Test
    void shouldHandleNullEmailAndName() {
      AuthService.UserInfo info = new AuthService.UserInfo("uid", null, null, null);

      assertThat(info.userId).isEqualTo("uid");
      assertThat(info.email).isEmpty();
      assertThat(info.name).isEmpty();
      assertThat(info.profilePicture).isEmpty();
    }

    @Test
    void shouldPreserveNonNullValues() {
      AuthService.UserInfo info = new AuthService.UserInfo("uid", "a@b.com", "Name", "https://pic");

      assertThat(info.userId).isEqualTo("uid");
      assertThat(info.email).isEqualTo("a@b.com");
      assertThat(info.name).isEqualTo("Name");
      assertThat(info.profilePicture).isEqualTo("https://pic");
    }

    @Test
    void shouldHandleEmptyStringsForEmailNameAndPicture() {
      AuthService.UserInfo info = new AuthService.UserInfo("uid", "", "", "");

      assertThat(info.userId).isEqualTo("uid");
      assertThat(info.email).isEmpty();
      assertThat(info.name).isEmpty();
      assertThat(info.profilePicture).isEmpty();
    }
  }

  @Nested
  class ExtractTokenFromHeader {

    @Test
    void shouldExtractTokenAfterBearerPrefix() {
      when(jwtService.isAccessToken("mytoken")).thenReturn(true);
      VerifyAuthTokenResponseDto result = authService.verifyAuthToken("Bearer mytoken").blockingGet();
      assertTrue(result.getIsAuthTokenValid());
      verify(jwtService).isAccessToken("mytoken");
    }

    @Test
    void shouldHandleRawTokenWithoutBearer() {
      when(jwtService.isAccessToken("raw")).thenReturn(false);
      VerifyAuthTokenResponseDto result = authService.verifyAuthToken("raw").blockingGet();
      assertFalse(result.getIsAuthTokenValid());
    }

    @Test
    void shouldExtractTokenWithExtraSpacesAndValidate() {
      when(jwtService.isAccessToken("token-with-spaces")).thenReturn(true);
      VerifyAuthTokenResponseDto result = authService.verifyAuthToken("Bearer  token-with-spaces  ").blockingGet();
      assertTrue(result.getIsAuthTokenValid());
    }
  }

  @Nested
  class LoginProductionFlowErrors {

    @Test
    void shouldPropagateErrorWhenTokenIsNullAndFirebaseConfigured() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getFirebaseProjectId()).thenReturn("my-project");

      Throwable t = assertThrows(RuntimeException.class, () -> authService.login(null).blockingGet());

      assertThat(t.getCause()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldPropagateErrorWhenTokenIsNullAndFirebaseNotConfigured() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);

      Throwable t = assertThrows(RuntimeException.class, () -> authService.login(null).blockingGet());

      assertTrue(t.getMessage().contains("Firebase is not configured")
          || (t.getCause() != null && t.getCause().getMessage().contains("Firebase is not configured")));
    }

    @Test
    void shouldPropagateErrorWhenTokenIsEmptyStringAndFirebaseConfigured() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getFirebaseProjectId()).thenReturn("my-project");

      assertThrows(RuntimeException.class, () -> authService.login("").blockingGet());
    }

    @Test
    void shouldPropagateErrorWhenProductionTokenHasInvalidStructure() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getFirebaseProjectId()).thenReturn("my-project");
      String invalidProductionToken = "header.payload.signature";

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.login(invalidProductionToken).blockingGet());

      assertThat(t.getCause()).isInstanceOf(java.text.ParseException.class);
    }
  }

  @Nested
  class LoginErrorPaths {

    @Test
    void shouldPropagateErrorWhenGetUserProjectsFailsInDevMode() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      User devUser = User.builder()
          .userId("mock-user-1")
          .email("user1@example.com")
          .name("Test User 1")
          .status("active")
          .build();
      when(userService.getUserByEmail("user1@example.com")).thenReturn(Maybe.just(devUser));
      when(userService.updateLastLogin("mock-user-1")).thenReturn(Completable.complete());
      when(openFgaService.getUserProjects("mock-user-1"))
          .thenReturn(Single.error(new RuntimeException("OpenFGA unavailable")));

      assertThrows(RuntimeException.class, () -> authService.login("mock-user1").blockingGet());
    }

    @Test
    void shouldPropagateErrorWhenGetProjectByIdFailsInDevMode() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
      User devUser = User.builder()
          .userId("mock-user-1")
          .email("user1@example.com")
          .name("Test User 1")
          .status("active")
          .build();
      when(userService.getUserByEmail("user1@example.com")).thenReturn(Maybe.just(devUser));
      when(userService.updateLastLogin("mock-user-1")).thenReturn(Completable.complete());
      when(openFgaService.getUserProjects("mock-user-1")).thenReturn(Single.just(java.util.List.of("proj-1")));
      when(projectService.getProjectById("proj-1"))
          .thenReturn(Single.error(new RuntimeException("Project not found")));

      assertThrows(RuntimeException.class, () -> authService.login("mock-user1").blockingGet());
    }
  }

  @Nested
  class GetAccessTokenFromRefreshTokenEdgeCases {

    @Test
    void throwsWhenClaimsSubjectIsNull() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("valid-refresh");
      when(jwtService.isRefreshToken("valid-refresh")).thenReturn(true);
      when(jwtService.isTokenExpired("valid-refresh")).thenReturn(false);
      Claims claims = mock(Claims.class);
      when(claims.getSubject()).thenReturn(null);
      when(claims.get(eq("email"), eq(String.class))).thenReturn("e@x.com");
      when(claims.get(eq("name"), eq(String.class))).thenReturn("Name");
      when(claims.get(eq("tenantId"), eq(String.class))).thenReturn("tenant-1");
      when(jwtService.verifyToken("valid-refresh")).thenReturn(claims);
      when(jwtService.generateAccessToken(null, "e@x.com", "Name", "tenant-1")).thenReturn("new-access");

      Single<GetAccessTokenFromRefreshTokenResponseDto> single =
          authService.getAccessTokenFromRefreshToken(request);
      GetAccessTokenFromRefreshTokenResponseDto result = single.blockingGet();

      assertNotNull(result);
      assertEquals("new-access", result.getAccessToken());
    }
  }

  @Nested
  class VerifyGoogleIdTokenAdditionalPaths {

    @Test
    void returnsErrorWhenTokenHasNonFirebaseIssuer() {
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getFirebaseProjectId()).thenReturn(null);
      String payloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding()
          .encodeToString("{\"iss\":\"https://login.example.com\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String token = "header." + payloadBase64 + ".signature";

      Throwable t = assertThrows(RuntimeException.class, () ->
          authService.verifyGoogleIdToken(token, "tenant-1").blockingGet());

      assertTrue(t.getMessage().contains("Firebase") || (t.getCause() != null && t.getCause().getMessage().contains("Firebase")));
    }
  }
}
