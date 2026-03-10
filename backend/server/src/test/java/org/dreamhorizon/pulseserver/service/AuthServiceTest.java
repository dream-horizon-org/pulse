package org.dreamhorizon.pulseserver.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.userdao.UserDao;
import org.dreamhorizon.pulseserver.dto.request.GetAccessTokenFromRefreshTokenRequestDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthenticateResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.GetAccessTokenFromRefreshTokenResponseDto;
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
  UserDao userDao;

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
  }

  @Nested
  class GetAccessTokenFromRefreshToken {

    @Test
    void throwsWhenRefreshTokenNull() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken(null);

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());
      assertTrue(ex.getCause() instanceof IllegalArgumentException);
      assertTrue(ex.getCause().getMessage().contains("Refresh token is required"));
    }

    @Test
    void throwsWhenRefreshTokenEmpty() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("   ");

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());
      assertTrue(ex.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void throwsWhenNotRefreshToken() {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("not-refresh");
      when(jwtService.isRefreshToken("not-refresh")).thenReturn(false);

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());
      assertTrue(ex.getCause() instanceof IllegalArgumentException);
      assertTrue(ex.getCause().getMessage().contains("Invalid token type"));
    }

    @Test
    void returnsNewAccessTokenWhenValidRefreshToken() throws Exception {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("valid-refresh");
      when(jwtService.isRefreshToken("valid-refresh")).thenReturn(true);
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
      request.setRefreshToken("expired-refresh");
      when(jwtService.isRefreshToken("expired-refresh")).thenReturn(true);
      when(jwtService.verifyToken("expired-refresh")).thenThrow(new RuntimeException("Token expired"));

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          authService.getAccessTokenFromRefreshToken(request).blockingGet());

      assertTrue(ex.getMessage().contains("Failed to refresh access token"));
      assertNotNull(ex.getCause());
    }

    @Test
    void returnsNewAccessTokenWhenClaimsHaveNullEmailAndName() throws Exception {
      GetAccessTokenFromRefreshTokenRequestDto request = new GetAccessTokenFromRefreshTokenRequestDto();
      request.setRefreshToken("valid-refresh");
      when(jwtService.isRefreshToken("valid-refresh")).thenReturn(true);
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
}
