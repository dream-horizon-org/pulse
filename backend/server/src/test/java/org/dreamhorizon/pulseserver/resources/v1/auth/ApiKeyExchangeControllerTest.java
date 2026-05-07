package org.dreamhorizon.pulseserver.resources.v1.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.dao.user.UserDao;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.userapikey.UserApiKeyService;
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
class ApiKeyExchangeControllerTest {

  @Mock
  UserApiKeyService userApiKeyService;

  @Mock
  UserDao userDao;

  @Mock
  JwtService jwtService;

  @Mock
  OpenFgaService openFgaService;

  ApiKeyExchangeController controller;

  @BeforeEach
  void setUp() {
    controller = new ApiKeyExchangeController(userApiKeyService, userDao, jwtService, openFgaService);
  }

  private <T> Response<T> await(CompletionStage<Response<T>> stage) {
    try {
      return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private ApiKeyExchangeController.ExchangeRequest request(String apiKey) {
    ApiKeyExchangeController.ExchangeRequest r = new ApiKeyExchangeController.ExchangeRequest();
    r.setApiKey(apiKey);
    return r;
  }

  private User sampleUser() {
    return User.builder()
        .userId("user-1")
        .email("a@b.com")
        .name("Alice")
        .build();
  }

  @Nested
  class Exchange {

    @Test
    void shouldReturnTokensWhenKeyValidUserAndTenantsPresent() {
      when(userApiKeyService.validateAndGetUserId("raw-key")).thenReturn(Maybe.just("user-1"));
      when(userDao.getUserById("user-1")).thenReturn(Maybe.just(sampleUser()));
      when(openFgaService.getUserTenants("user-1")).thenReturn(Single.just(List.of("tenant-a", "tenant-b")));
      when(jwtService.generateAccessToken(eq("user-1"), eq("a@b.com"), eq("Alice"), eq("tenant-a")))
          .thenReturn("access-jwt");
      when(jwtService.generateRefreshToken(eq("user-1"), eq("a@b.com"), eq("Alice"), eq("tenant-a")))
          .thenReturn("refresh-jwt");

      Response<ApiKeyExchangeController.ExchangeResponse> response =
          await(controller.exchange(request("raw-key")));

      assertThat(response.getData().getAccessToken()).isEqualTo("access-jwt");
      assertThat(response.getData().getRefreshToken()).isEqualTo("refresh-jwt");
      verify(jwtService).generateAccessToken(anyString(), anyString(), anyString(), eq("tenant-a"));
    }

    @Test
    void shouldErrorWhenApiKeyUnknown() {
      when(userApiKeyService.validateAndGetUserId("bad")).thenReturn(Maybe.empty());

      assertThrows(RuntimeException.class, () -> await(controller.exchange(request("bad"))));
    }

    @Test
    void shouldErrorWhenUserMissing() {
      when(userApiKeyService.validateAndGetUserId("k")).thenReturn(Maybe.just("user-x"));
      when(userDao.getUserById("user-x")).thenReturn(Maybe.empty());

      assertThrows(RuntimeException.class, () -> await(controller.exchange(request("k"))));
    }

    @Test
    void shouldErrorWhenUserHasNoTenant() {
      when(userApiKeyService.validateAndGetUserId("k")).thenReturn(Maybe.just("user-1"));
      when(userDao.getUserById("user-1")).thenReturn(Maybe.just(sampleUser()));
      when(openFgaService.getUserTenants("user-1")).thenReturn(Single.just(List.of()));

      assertThrows(RuntimeException.class, () -> await(controller.exchange(request("k"))));
    }
  }
}
