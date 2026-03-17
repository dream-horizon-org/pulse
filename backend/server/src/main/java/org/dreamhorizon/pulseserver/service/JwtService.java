package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class JwtService {

  private static final long ACCESS_TOKEN_VALIDITY_MS = 86400000L;
  private static final long REFRESH_TOKEN_VALIDITY_MS = 2592000000L;
  public static final int ACCESS_TOKEN_VALIDITY_SECONDS = 86400;
  private static final String TOKEN_TYPE_ACCESS = "access";
  private static final String TOKEN_TYPE_REFRESH = "refresh";
  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_NAME = "name";
  private static final String CLAIM_TYPE = "type";
  private static final String CLAIM_TENANT_ID = "tenantId";
  private static final int MINIMUM_SECRET_LENGTH = 32;

  private final ApplicationConfig applicationConfig;
  private SecretKey signingKey;

  private SecretKey getSigningKey() {
    if (signingKey == null) {
      String secret = applicationConfig.getJwtSecret();
      if (secret == null || secret.trim().isEmpty()) {
        throw new IllegalStateException("JWT secret is not configured");
      }

      byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
      if (keyBytes.length < MINIMUM_SECRET_LENGTH) {
        throw new IllegalStateException(
            String.format("JWT secret must be at least %d characters long", MINIMUM_SECRET_LENGTH)
        );
      }

      signingKey = Keys.hmacShaKeyFor(keyBytes);
      log.info("JWT signing key initialized");
    }
    return signingKey;
  }

  public String generateAccessToken(String userId, String email, String name) {
    return generateAccessToken(userId, email, name, null);
  }

  public String generateAccessToken(String userId, String email, String name, String tenantId) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + ACCESS_TOKEN_VALIDITY_MS);

    var builder = Jwts.builder()
        .subject(userId)
        .claim(CLAIM_EMAIL, email)
        .claim(CLAIM_NAME, name)
        .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS);

    if (tenantId != null) {
      builder.claim(CLAIM_TENANT_ID, tenantId);
    }

    return builder
        .issuedAt(now)
        .expiration(expiry)
        .signWith(getSigningKey())
        .compact();
  }

  public String generateRefreshToken(String userId, String email, String name) {
    return generateRefreshToken(userId, email, name, null);
  }

  public String generateRefreshToken(String userId, String email, String name, String tenantId) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + REFRESH_TOKEN_VALIDITY_MS);

    var builder = Jwts.builder()
        .subject(userId)
        .claim(CLAIM_EMAIL, email)
        .claim(CLAIM_NAME, name)
        .claim(CLAIM_TYPE, TOKEN_TYPE_REFRESH);

    if (tenantId != null) {
      builder.claim(CLAIM_TENANT_ID, tenantId);
    }

    return builder
        .issuedAt(now)
        .expiration(expiry)
        .signWith(getSigningKey())
        .compact();
  }

  public Claims verifyToken(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  /**
   * Parse claims from a token even if it is expired.
   * Signature is still verified; only expiration is tolerated.
   * Throws for malformed or tampered tokens.
   */
  public Claims parseClaimsAllowingExpired(String token) {
    try {
      return verifyToken(token);
    } catch (ExpiredJwtException e) {
      return e.getClaims();
    }
  }

  public boolean isTokenExpired(String token) {
    try {
      verifyToken(token);
      return false;
    } catch (ExpiredJwtException e) {
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public boolean isAccessToken(String token) {
    try {
      Claims claims = parseClaimsAllowingExpired(token);
      return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    } catch (Exception e) {
      return false;
    }
  }

  public boolean isRefreshToken(String token) {
    try {
      Claims claims = parseClaimsAllowingExpired(token);
      return TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    } catch (Exception e) {
      return false;
    }
  }

  public String generateIdToken(String userId, String email, String firstName, String lastName, String profilePicture) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + ACCESS_TOKEN_VALIDITY_MS);

    return Jwts.builder()
        .subject(userId)
        .claim(CLAIM_EMAIL, email)
        .claim("firstName", firstName)
        .claim("lastName", lastName)
        .claim("profilePicture", profilePicture)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(getSigningKey())
        .compact();
  }
}