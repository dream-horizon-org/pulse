package org.dreamhorizon.pulseserver.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UtilityClassesTest {

  @Nested
  class AuthenticationUtilTests {

    @Test
    void shouldExtractTokenFromBearerHeader() {
      String token = org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractToken("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxx");
      assertThat(token).isEqualTo("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxx");
    }

    @Test
    void shouldThrowWhenAuthorizationIsNull() {
      assertThatThrownBy(() -> org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractToken(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Authorization header is required");
    }

    @Test
    void shouldThrowWhenAuthorizationIsEmpty() {
      assertThatThrownBy(() -> org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractToken(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Authorization header is required");
    }

    @Test
    void shouldThrowWhenAuthorizationDoesNotStartWithBearer() {
      assertThatThrownBy(() -> org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractToken("Basic abc123"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Bearer ");
    }

    @Test
    void shouldExtractUserIdFromValidJwt() throws Exception {
      SignedJWT jwt = createSignedJwt("user-123", null);
      String auth = "Bearer " + jwt.serialize();
      String userId = org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractUserId(auth);
      assertThat(userId).isEqualTo("user-123");
    }

    @Test
    void shouldThrowWhenTokenMissingSubject() throws Exception {
      SignedJWT jwt = createSignedJwt(null, null);
      String auth = "Bearer " + jwt.serialize();
      assertThatThrownBy(() -> org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractUserId(auth))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("subject");
    }

    @Test
    void shouldExtractTenantIdFromJwt() throws Exception {
      SignedJWT jwt = createSignedJwtWithTenant("user-1", "tenant-456");
      String auth = "Bearer " + jwt.serialize();
      String tenantId = org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractTenantId(auth);
      assertThat(tenantId).isEqualTo("tenant-456");
    }

    @Test
    void shouldReturnNullTenantIdWhenNotPresent() throws Exception {
      SignedJWT jwt = createSignedJwt("user-1", null);
      String auth = "Bearer " + jwt.serialize();
      String tenantId = org.dreamhorizon.pulseserver.util.AuthenticationUtil.extractTenantId(auth);
      assertThat(tenantId).isNull();
    }

    private SignedJWT createSignedJwt(String subject, String tenantId) throws Exception {
      JWTClaimsSet claims = new JWTClaimsSet.Builder()
          .subject(subject)
          .claim("tenantId", tenantId)
          .expirationTime(new Date(System.currentTimeMillis() + 3600000))
          .build();
      KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
      SignedJWT jwt = new SignedJWT(
          new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256),
          claims);
      jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(keyPair.getPrivate()));
      return jwt;
    }

    private SignedJWT createSignedJwtWithTenant(String subject, String tenantId) throws Exception {
      return createSignedJwt(subject, tenantId);
    }
  }

  @Nested
  class ApiKeyGeneratorTests {

    org.dreamhorizon.pulseserver.util.ApiKeyGenerator generator = new org.dreamhorizon.pulseserver.util.ApiKeyGenerator();

    @Test
    void shouldGenerateApiKeyWithCorrectFormat() {
      String apiKey = generator.generate("proj123");
      assertThat(apiKey).startsWith("pulse_proj123_sk_");
    }

    @Test
    void shouldExtractProjectIdFromValidApiKey() {
      String apiKey = "pulse_myproj_sk_randomPartHere";
      String projectId = generator.extractProjectId(apiKey);
      assertThat(projectId).isEqualTo("myproj");
    }

    @Test
    void shouldThrowWhenExtractProjectIdFromNull() {
      assertThatThrownBy(() -> generator.extractProjectId(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void shouldThrowWhenExtractProjectIdFromEmpty() {
      assertThatThrownBy(() -> generator.extractProjectId(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void shouldThrowWhenApiKeyFormatInvalid() {
      assertThatThrownBy(() -> generator.extractProjectId("invalid-key"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid API key format");
    }

    @Test
    void shouldReturnTrueForValidFormat() {
      String apiKey = "pulse_proj123_sk_randomKeyPartHere";
      assertThat(generator.isValidFormat(apiKey)).isTrue();
    }

    @Test
    void shouldReturnFalseForInvalidFormat() {
      assertThat(generator.isValidFormat("invalid")).isFalse();
    }

    @Test
    void shouldGenerateUniqueApiKeys() {
      String key1 = generator.generate("proj1");
      String key2 = generator.generate("proj1");
      assertThat(key1).isNotEqualTo(key2);
    }
  }

  @Nested
  class SecureRandomUtilTests {

    @Test
    void shouldGenerateAlphanumericStringOfCorrectLength() {
      String result = org.dreamhorizon.pulseserver.util.SecureRandomUtil.generateAlphanumeric(32);
      assertThat(result).hasSize(32);
      assertThat(result).matches("[A-Za-z0-9]+");
    }

    @Test
    void shouldThrowWhenLengthIsZero() {
      assertThatThrownBy(() -> org.dreamhorizon.pulseserver.util.SecureRandomUtil.generateAlphanumeric(0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("positive");
    }

    @Test
    void shouldThrowWhenLengthIsNegative() {
      assertThatThrownBy(() -> org.dreamhorizon.pulseserver.util.SecureRandomUtil.generateAlphanumeric(-1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("positive");
    }

    @Test
    void shouldGenerateBytesOfCorrectLength() {
      byte[] result = org.dreamhorizon.pulseserver.util.SecureRandomUtil.generateBytes(16);
      assertThat(result).hasSize(16);
    }

    @Test
    void shouldThrowWhenBytesLengthIsZero() {
      assertThatThrownBy(() -> org.dreamhorizon.pulseserver.util.SecureRandomUtil.generateBytes(0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("positive");
    }

    @Test
    void shouldGenerateDifferentBytesEachCall() {
      byte[] bytes1 = org.dreamhorizon.pulseserver.util.SecureRandomUtil.generateBytes(32);
      byte[] bytes2 = org.dreamhorizon.pulseserver.util.SecureRandomUtil.generateBytes(32);
      assertThat(bytes1).isNotEqualTo(bytes2);
    }
  }
}
