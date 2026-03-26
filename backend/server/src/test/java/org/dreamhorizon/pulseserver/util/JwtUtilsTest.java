package org.dreamhorizon.pulseserver.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JwtUtilsTest {

  private static String encodePayload(String payload) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  private static String createJwt(String payload) {
    String header = encodePayload("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    String payloadB64 = encodePayload(payload);
    return header + "." + payloadB64 + ".signature";
  }

  @Nested
  class ExtractUserId {

    @Test
    void shouldExtractUserIdFromSubClaim() {
      String token = createJwt("{\"sub\":\"user-123\",\"email\":\"a@b.com\"}");
      assertThat(JwtUtils.extractUserId(token)).isEqualTo("user-123");
    }

    @Test
    void shouldExtractUserIdFromSubjectClaim() {
      String token = createJwt("{\"subject\":\"user-456\"}");
      assertThat(JwtUtils.extractUserId(token)).isEqualTo("user-456");
    }

    @Test
    void shouldPreferSubOverSubject() {
      String token = createJwt("{\"sub\":\"sub-user\",\"subject\":\"subject-user\"}");
      assertThat(JwtUtils.extractUserId(token)).isEqualTo("sub-user");
    }

    @Test
    void shouldReturnNullWhenNoUserIdInToken() {
      String token = createJwt("{\"email\":\"a@b.com\"}");

      assertThat(JwtUtils.extractUserId(token)).isNull();
    }

    @Test
    void shouldThrowWhenInvalidTokenFormat() {
      assertThatThrownBy(() -> JwtUtils.extractUserId("not.valid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid token");
    }

    @Test
    void shouldThrowWhenTokenHasTwoParts() {
      assertThatThrownBy(() -> JwtUtils.extractUserId("part1.part2"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid token");
    }

    @Test
    void shouldThrowWhenTokenHasInvalidBase64() {
      assertThatThrownBy(() -> JwtUtils.extractUserId("a.b!!!.c"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid token");
    }
  }

  @Nested
  class ExtractTenantId {

    @Test
    void shouldExtractTenantIdWhenPresent() {
      String token = createJwt("{\"sub\":\"u1\",\"tenantId\":\"tenant-abc\"}");
      assertThat(JwtUtils.extractTenantId(token)).isEqualTo("tenant-abc");
    }

    @Test
    void shouldReturnNullWhenTenantIdMissing() {
      String token = createJwt("{\"sub\":\"u1\"}");
      assertThat(JwtUtils.extractTenantId(token)).isNull();
    }

    @Test
    void shouldReturnNullWhenTokenInvalid() {
      assertThat(JwtUtils.extractTenantId("invalid")).isNull();
    }
  }

  @Nested
  class ExtractEmail {

    @Test
    void shouldExtractEmailWhenPresent() {
      String token = createJwt("{\"sub\":\"u1\",\"email\":\"user@example.com\"}");
      assertThat(JwtUtils.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    void shouldReturnNullWhenEmailMissing() {
      String token = createJwt("{\"sub\":\"u1\"}");
      assertThat(JwtUtils.extractEmail(token)).isNull();
    }

    @Test
    void shouldReturnNullWhenTokenInvalid() {
      assertThat(JwtUtils.extractEmail("invalid")).isNull();
    }
  }

  @Nested
  class JwtIssuer {

    @Test
    void shouldExtractIssuerWhenPresent() {
      String token = createJwt("{\"sub\":\"u1\",\"iss\":\"https://securetoken.google.com/proj1\"}");
      assertThat(JwtUtils.jwtIssuer(token)).isEqualTo("https://securetoken.google.com/proj1");
    }

    @Test
    void shouldReturnNullWhenIssuerMissing() {
      String token = createJwt("{\"sub\":\"u1\"}");
      assertThat(JwtUtils.jwtIssuer(token)).isNull();
    }

    @Test
    void shouldReturnNullWhenTokenInvalid() {
      assertThat(JwtUtils.jwtIssuer("invalid")).isNull();
    }
  }

  @Nested
  class IsFirebaseIssuer {

    @Test
    void shouldReturnTrueForFirebaseIssuer() {
      assertThat(JwtUtils.isFirebaseIssuer("https://securetoken.google.com/proj1")).isTrue();
      assertThat(JwtUtils.isFirebaseIssuer("https://securetoken.google.com/")).isTrue();
    }

    @Test
    void shouldReturnFalseForNonFirebaseIssuer() {
      assertThat(JwtUtils.isFirebaseIssuer("https://accounts.google.com")).isFalse();
      assertThat(JwtUtils.isFirebaseIssuer("https://login.example.com")).isFalse();
    }

    @Test
    void shouldReturnFalseWhenIssuerNull() {
      assertThat(JwtUtils.isFirebaseIssuer(null)).isFalse();
    }
  }
}
