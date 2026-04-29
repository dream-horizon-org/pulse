package org.dreamhorizon.pulseserver.resources.v1.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.userapikey.UserApiKeyService;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyInfo;
import org.dreamhorizon.pulseserver.service.userapikey.models.UserApiKeyPublicInfo;
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
class UserApiKeyControllerTest {

  private static final String BEARER = "Bearer good-token";

  @Mock
  UserApiKeyService userApiKeyService;

  @Mock
  JwtService jwtService;

  @Mock
  Claims claims;

  UserApiKeyController controller;

  @BeforeEach
  void setUp() {
    controller = new UserApiKeyController(userApiKeyService, jwtService);
  }

  private <T> Response<T> await(CompletionStage<Response<T>> stage) {
    try {
      return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private void stubValidJwt(String subject) {
    when(claims.getSubject()).thenReturn(subject);
    when(jwtService.verifyToken("good-token")).thenReturn(claims);
  }

  @Nested
  class Authorisation {

    @Test
    void shouldRejectNullAuthorizationOnList() {
      assertThrows(WebApplicationException.class, () -> controller.listApiKeys(null));
    }

    @Test
    void shouldRejectNonBearerAuthorizationOnList() {
      assertThrows(WebApplicationException.class, () -> controller.listApiKeys("Basic x"));
    }

    @Test
    void shouldRejectInvalidJwtOnList() {
      when(jwtService.verifyToken("bad")).thenThrow(new RuntimeException("invalid"));
      assertThrows(WebApplicationException.class, () -> controller.listApiKeys("Bearer bad"));
    }
  }

  @Nested
  class ListApiKeys {

    @Test
    void shouldReturnKeysForAuthenticatedUser() {
      stubValidJwt("user-1");
      UserApiKeyPublicInfo row = UserApiKeyPublicInfo.builder()
          .id(1L)
          .displayName("k")
          .keyPrefix("pulse_mcp_0123456789")
          .isActive(true)
          .createdAt(Instant.now())
          .build();
      when(userApiKeyService.listApiKeys("user-1")).thenReturn(Single.just(List.of(row)));

      Response<List<UserApiKeyPublicInfo>> response = await(controller.listApiKeys(BEARER));

      assertThat(response.getData()).hasSize(1);
      assertThat(response.getData().get(0).getId()).isEqualTo(1L);
      verify(userApiKeyService).listApiKeys("user-1");
    }
  }

  @Nested
  class CreateApiKey {

    @Test
    void shouldCreateKey() {
      stubValidJwt("user-1");
      UserApiKeyController.CreateUserApiKeyRequest req = new UserApiKeyController.CreateUserApiKeyRequest();
      req.setDisplayName("MCP");
      UserApiKeyInfo created = UserApiKeyInfo.builder()
          .id(2L)
          .displayName("MCP")
          .rawApiKey("pulse_mcp_secret")
          .keyPrefix("pulse_mcp_0123456789")
          .createdAt(Instant.now())
          .build();
      when(userApiKeyService.createApiKey("user-1", "MCP")).thenReturn(Single.just(created));

      Response<UserApiKeyInfo> response = await(controller.createApiKey(BEARER, req));

      assertThat(response.getData().getId()).isEqualTo(2L);
      verify(userApiKeyService).createApiKey(eq("user-1"), eq("MCP"));
    }
  }

  @Nested
  class RevokeApiKey {

    @Test
    void shouldRevokeKey() {
      stubValidJwt("user-1");
      when(userApiKeyService.revokeApiKey(7L, "user-1", "user-1")).thenReturn(Completable.complete());

      Response<Void> response = await(controller.revokeApiKey(BEARER, 7L));

      assertThat(response.getData()).isNull();
      verify(userApiKeyService).revokeApiKey(7L, "user-1", "user-1");
    }
  }
}
