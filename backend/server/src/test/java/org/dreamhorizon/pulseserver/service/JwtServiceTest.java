package org.dreamhorizon.pulseserver.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

  private static final String VALID_SECRET = "this-is-a-very-long-secret-key-for-jwt-signing-purposes";

  @Mock
  private ApplicationConfig applicationConfig;

  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    jwtService = new JwtService(applicationConfig);
  }

  @Nested
  class GenerateAccessTokenTests {

    @Test
    void shouldGenerateAccessTokenWithoutTenantId() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateAccessToken("user-123", "test@example.com", "Test User");

      assertNotNull(token);
      assertTrue(token.split("\\.").length == 3); // JWT has 3 parts
    }

    @Test
    void shouldGenerateAccessTokenWithTenantId() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateAccessToken("user-123", "test@example.com", "Test User", "tenant-456");

      assertNotNull(token);

      Claims claims = jwtService.verifyToken(token);
      assertEquals("tenant-456", claims.get("tenantId", String.class));
    }

    @Test
    void shouldIncludeEmailAndNameInAccessToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateAccessToken("user-123", "test@example.com", "Test User");

      Claims claims = jwtService.verifyToken(token);
      assertEquals("user-123", claims.getSubject());
      assertEquals("test@example.com", claims.get("email", String.class));
      assertEquals("Test User", claims.get("name", String.class));
      assertEquals("access", claims.get("type", String.class));
    }
  }

  @Nested
  class GenerateRefreshTokenTests {

    @Test
    void shouldGenerateRefreshTokenWithoutTenantId() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateRefreshToken("user-123", "test@example.com", "Test User");

      assertNotNull(token);
      assertTrue(token.split("\\.").length == 3);
    }

    @Test
    void shouldGenerateRefreshTokenWithTenantId() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateRefreshToken("user-123", "test@example.com", "Test User", "tenant-456");

      assertNotNull(token);

      Claims claims = jwtService.verifyToken(token);
      assertEquals("tenant-456", claims.get("tenantId", String.class));
    }

    @Test
    void shouldIncludeRefreshTypeInToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateRefreshToken("user-123", "test@example.com", "Test User");

      Claims claims = jwtService.verifyToken(token);
      assertEquals("refresh", claims.get("type", String.class));
    }
  }

  @Nested
  class VerifyTokenTests {

    @Test
    void shouldVerifyValidToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateAccessToken("user-123", "test@example.com", "Test User");
      Claims claims = jwtService.verifyToken(token);

      assertNotNull(claims);
      assertEquals("user-123", claims.getSubject());
    }

    @Test
    void shouldThrowOnInvalidToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      assertThrows(Exception.class, () -> jwtService.verifyToken("invalid-token"));
    }

    @Test
    void shouldThrowOnMalformedToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      assertThrows(Exception.class, () -> jwtService.verifyToken("not.a.valid.jwt.token"));
    }
  }

  @Nested
  class IsAccessTokenTests {

    @Test
    void shouldReturnTrueForAccessToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateAccessToken("user-123", "test@example.com", "Test User");

      assertTrue(jwtService.isAccessToken(token));
    }

    @Test
    void shouldReturnFalseForRefreshToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateRefreshToken("user-123", "test@example.com", "Test User");

      assertFalse(jwtService.isAccessToken(token));
    }

    @Test
    void shouldReturnFalseForInvalidToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      assertFalse(jwtService.isAccessToken("invalid-token"));
    }
  }

  @Nested
  class IsRefreshTokenTests {

    @Test
    void shouldReturnTrueForRefreshToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateRefreshToken("user-123", "test@example.com", "Test User");

      assertTrue(jwtService.isRefreshToken(token));
    }

    @Test
    void shouldReturnFalseForAccessToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateAccessToken("user-123", "test@example.com", "Test User");

      assertFalse(jwtService.isRefreshToken(token));
    }

    @Test
    void shouldReturnFalseForInvalidToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      assertFalse(jwtService.isRefreshToken("invalid-token"));
    }
  }

  @Nested
  class GenerateIdTokenTests {

    @Test
    void shouldGenerateIdToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateIdToken(
          "user-123",
          "test@example.com",
          "Test",
          "User",
          "https://example.com/profile.jpg"
      );

      assertNotNull(token);
      assertTrue(token.split("\\.").length == 3);
    }

    @Test
    void shouldIncludeUserInfoInIdToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateIdToken(
          "user-123",
          "test@example.com",
          "Test",
          "User",
          "https://example.com/profile.jpg"
      );

      Claims claims = jwtService.verifyToken(token);
      assertEquals("user-123", claims.getSubject());
      assertEquals("test@example.com", claims.get("email", String.class));
      assertEquals("Test", claims.get("firstName", String.class));
      assertEquals("User", claims.get("lastName", String.class));
      assertEquals("https://example.com/profile.jpg", claims.get("profilePicture", String.class));
    }

    @Test
    void shouldHandleNullProfilePicture() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateIdToken(
          "user-123",
          "test@example.com",
          "Test",
          "User",
          null
      );

      Claims claims = jwtService.verifyToken(token);
      assertNull(claims.get("profilePicture", String.class));
    }
  }

  @Nested
  class SecretValidationTests {

    @Test
    void shouldThrowWhenSecretIsNull() {
      when(applicationConfig.getJwtSecret()).thenReturn(null);

      assertThrows(IllegalStateException.class, () ->
          jwtService.generateAccessToken("user-123", "test@example.com", "Test User"));
    }

    @Test
    void shouldThrowWhenSecretIsEmpty() {
      when(applicationConfig.getJwtSecret()).thenReturn("");

      assertThrows(IllegalStateException.class, () ->
          jwtService.generateAccessToken("user-123", "test@example.com", "Test User"));
    }

    @Test
    void shouldThrowWhenSecretIsWhitespace() {
      when(applicationConfig.getJwtSecret()).thenReturn("   ");

      assertThrows(IllegalStateException.class, () ->
          jwtService.generateAccessToken("user-123", "test@example.com", "Test User"));
    }

    @Test
    void shouldThrowWhenSecretIsTooShort() {
      when(applicationConfig.getJwtSecret()).thenReturn("short");

      assertThrows(IllegalStateException.class, () ->
          jwtService.generateAccessToken("user-123", "test@example.com", "Test User"));
    }

    @Test
    void shouldAcceptSecretOfExactMinimumLength() {
      // 32 characters is the minimum
      String secret32Chars = "12345678901234567890123456789012";
      when(applicationConfig.getJwtSecret()).thenReturn(secret32Chars);

      String token = jwtService.generateAccessToken("user-123", "test@example.com", "Test User");
      assertNotNull(token);
    }
  }

  @Nested
  class TokenExpirationTests {

    @Test
    void shouldHaveExpirationDateInAccessToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateAccessToken("user-123", "test@example.com", "Test User");
      Claims claims = jwtService.verifyToken(token);

      assertNotNull(claims.getExpiration());
      assertNotNull(claims.getIssuedAt());
      assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void shouldHaveExpirationDateInRefreshToken() {
      when(applicationConfig.getJwtSecret()).thenReturn(VALID_SECRET);

      String token = jwtService.generateRefreshToken("user-123", "test@example.com", "Test User");
      Claims claims = jwtService.verifyToken(token);

      assertNotNull(claims.getExpiration());
      assertNotNull(claims.getIssuedAt());
      assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }
  }
}

