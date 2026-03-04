package org.dreamhorizon.pulseserver.resources.v1.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.dto.request.GetAccessTokenFromRefreshTokenRequestDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthenticateRequestDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthenticateResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.GetAccessTokenFromRefreshTokenResponseDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.VerifyAuthTokenResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticateTest {

  @Mock
  AuthService authService;

  Authenticate authenticate;

  @BeforeEach
  void setUp() {
    authenticate = new Authenticate(authService);
  }

  @Test
  void getAccessAndRefreshTokensReturnsResponseFromAuthService(io.vertx.core.Vertx vertx,
      VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      AuthenticateRequestDto requestDto = new AuthenticateRequestDto();
      requestDto.identifier = "id-token";
      AuthenticateResponseDto authResponse = AuthenticateResponseDto.builder()
          .accessToken("access")
          .refreshToken("refresh")
          .idToken("id")
          .tokenType("Bearer")
          .expiresIn(86400)
          .build();
      when(authService.verifyGoogleIdToken(eq("id-token"), isNull()))
          .thenReturn(Single.just(authResponse));

      CompletionStage<Response<AuthenticateResponseDto>> result =
          authenticate.getAccessAndRefreshTokens(requestDto, null);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getAccessToken()).isEqualTo("access");
          assertThat(response.getData().getRefreshToken()).isEqualTo("refresh");
          verify(authService).verifyGoogleIdToken("id-token", null);
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void getAccessAndRefreshTokensWithTenantId(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      AuthenticateRequestDto requestDto = new AuthenticateRequestDto();
      requestDto.identifier = "firebase-token";
      AuthenticateResponseDto authResponse = AuthenticateResponseDto.builder()
          .accessToken("access")
          .refreshToken("refresh")
          .idToken("firebase-token")
          .tokenType("Bearer")
          .expiresIn(86400)
          .build();
      when(authService.verifyGoogleIdToken(eq("firebase-token"), eq("tenant-1")))
          .thenReturn(Single.just(authResponse));

      CompletionStage<Response<AuthenticateResponseDto>> result =
          authenticate.getAccessAndRefreshTokens(requestDto, "tenant-1");

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response.getData().getAccessToken()).isEqualTo("access");
          verify(authService).verifyGoogleIdToken("firebase-token", "tenant-1");
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void verifyAuthTokenReturnsResponseFromAuthService(io.vertx.core.Vertx vertx,
      VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      VerifyAuthTokenResponseDto authResponse = VerifyAuthTokenResponseDto.builder()
          .isAuthTokenValid(true)
          .build();
      when(authService.verifyAuthToken("Bearer token123")).thenReturn(Single.just(authResponse));

      CompletionStage<Response<VerifyAuthTokenResponseDto>> result =
          authenticate.verifyAuthToken("Bearer token123");

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getIsAuthTokenValid()).isTrue();
          verify(authService).verifyAuthToken("Bearer token123");
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void getAccessTokenFromRefreshTokenReturnsResponseFromAuthService(io.vertx.core.Vertx vertx,
      VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      GetAccessTokenFromRefreshTokenRequestDto requestDto = new GetAccessTokenFromRefreshTokenRequestDto();
      requestDto.setRefreshToken("refresh-token");
      GetAccessTokenFromRefreshTokenResponseDto authResponse =
          GetAccessTokenFromRefreshTokenResponseDto.builder()
              .accessToken("new-access")
              .refreshToken("refresh-token")
              .tokenType("Bearer")
              .expiresIn(86400)
              .build();
      when(authService.getAccessTokenFromRefreshToken(requestDto))
          .thenReturn(Single.just(authResponse));

      CompletionStage<Response<GetAccessTokenFromRefreshTokenResponseDto>> result =
          authenticate.getAccessTokenFromRefreshToken(requestDto);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getAccessToken()).isEqualTo("new-access");
          assertThat(response.getData().getRefreshToken()).isEqualTo("refresh-token");
          verify(authService).getAccessTokenFromRefreshToken(requestDto);
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void getAccessAndRefreshTokensThrowsWhenAuthServiceThrows() {
    AuthenticateRequestDto requestDto = new AuthenticateRequestDto();
    requestDto.identifier = "token";
    when(authService.verifyGoogleIdToken(eq("token"), any())).thenThrow(new RuntimeException("Invalid token"));

    assertThrows(Throwable.class, () ->
        authenticate.getAccessAndRefreshTokens(requestDto, null));
  }

  @Test
  void getAccessAndRefreshTokensThrowsWithNullMessageWhenAuthServiceThrowsNullMessage() {
    AuthenticateRequestDto requestDto = new AuthenticateRequestDto();
    requestDto.identifier = "token";
    when(authService.verifyGoogleIdToken(eq("token"), any()))
        .thenThrow(new RuntimeException());

    assertThrows(Throwable.class, () ->
        authenticate.getAccessAndRefreshTokens(requestDto, null));
  }

  @Test
  void verifyAuthTokenThrowsWhenAuthServiceThrows() {
    when(authService.verifyAuthToken("Bearer x")).thenThrow(new RuntimeException("error"));

    assertThrows(Throwable.class, () ->
        authenticate.verifyAuthToken("Bearer x"));
  }
}
