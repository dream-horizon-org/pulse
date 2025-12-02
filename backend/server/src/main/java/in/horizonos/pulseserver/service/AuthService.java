package in.horizonos.pulseserver.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.inject.Inject;
import in.horizonos.pulseserver.config.ApplicationConfig;
import in.horizonos.pulseserver.dto.request.GetAccessTokenFromRefreshTokenRequestDto;
import in.horizonos.pulseserver.resources.v1.auth.models.AuthenticateResponseDto;
import in.horizonos.pulseserver.resources.v1.auth.models.GetAccessTokenFromRefreshTokenResponseDto;
import in.horizonos.pulseserver.resources.v1.auth.models.VerifyAuthTokenResponseDto;
import io.jsonwebtoken.Claims;
import io.reactivex.rxjava3.core.Single;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AuthService {

  private final ApplicationConfig applicationConfig;
  private final JwtService jwtService;
  private GoogleIdTokenVerifier verifier;

  // Development mode constants
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

  private GoogleIdTokenVerifier getVerifier() {
    if (verifier == null) {
      verifier = new GoogleIdTokenVerifier.Builder(
          new NetHttpTransport(),
          GsonFactory.getDefaultInstance())
          .setAudience(Collections.singletonList(applicationConfig.getGoogleOAuthClientId()))
          .build();
    }
    return verifier;
  }


  private AuthenticateResponseDto createDevelopmentUser() {
    String accessToken = jwtService.generateAccessToken(DEV_USER_ID, DEV_EMAIL, DEV_NAME);
    String refreshToken = jwtService.generateRefreshToken(DEV_USER_ID, DEV_EMAIL, DEV_NAME);
    String idToken = jwtService.generateIdToken(DEV_USER_ID, DEV_EMAIL, DEV_FIRST_NAME, DEV_LAST_NAME, DEV_PROFILE_PICTURE);

    return AuthenticateResponseDto.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .idToken(idToken)
        .tokenType(TOKEN_TYPE_BEARER)
        .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
        .build();
  }


  public Single<AuthenticateResponseDto> verifyGoogleIdToken(String idTokenString) {
    if (!isGoogleSignInEnabled()) {
      return Single.just(createDevelopmentUser());
    }

    return Single.fromCallable(() -> {
      try {
        GoogleIdToken idToken = getVerifier().verify(idTokenString);

        if (idToken == null) {
          log.error("Invalid Google ID token");
          throw new IllegalArgumentException("Invalid ID token");
        }

        Payload payload = idToken.getPayload();

        String userId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get(CLAIM_NAME);

        String accessToken = jwtService.generateAccessToken(userId, email, name);
        String refreshToken = jwtService.generateRefreshToken(userId, email, name);

        return AuthenticateResponseDto.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .idToken(idTokenString)
            .tokenType(TOKEN_TYPE_BEARER)
            .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
            .build();

      } catch (GeneralSecurityException e) {
        log.error("Security error verifying Google ID token", e);
        throw new RuntimeException("Failed to verify ID token due to security error", e);
      } catch (IOException e) {
        log.error("IO error verifying Google ID token", e);
        throw new RuntimeException("Failed to verify ID token due to IO error", e);
      }
    });
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

        String newAccessToken = jwtService.generateAccessToken(userId, email, name);

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
