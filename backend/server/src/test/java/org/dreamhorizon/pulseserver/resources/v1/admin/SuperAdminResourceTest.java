package org.dreamhorizon.pulseserver.resources.v1.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import io.reactivex.rxjava3.core.Single;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import jakarta.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.GrantSuperAdminRequest;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.SuperAdminsListResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
class SuperAdminResourceTest {

  private static final String JWT_CALLER = "Bearer signed-token";

  @Mock
  Provider<OpenFgaService> openFgaProvider;

  @Mock
  OpenFgaService openFga;

  @Mock
  JwtService jwtService;

  @Mock
  UserService userService;

  @Mock
  Claims verifiedClaims;

  SuperAdminResource resource;

  @BeforeEach
  void setUp() {
    resource = new SuperAdminResource(openFgaProvider, jwtService, userService);
    lenient()
        .when(userService.getUsersByIds(anyList()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              List<String> ids = invocation.getArgument(0);
              List<User> out = new ArrayList<>();
              for (String id : ids) {
                out.add(
                    User.builder()
                        .userId(id)
                        .email(id.replace("user-", "") + "@example.com")
                        .build());
              }
              return Single.just(out);
            });
  }

  private void withVerifiedJwt(String subject) {
    when(verifiedClaims.getSubject()).thenReturn(subject);
    when(jwtService.isAccessToken("signed-token")).thenReturn(true);
    when(jwtService.verifyToken("signed-token")).thenReturn(verifiedClaims);
  }

  private void withEnabledOpenFga() {
    withVerifiedJwt("caller-1");
    when(openFgaProvider.get()).thenReturn(openFga);
    when(openFga.isEnabled()).thenReturn(true);
  }

  private static Throwable unwrap(Throwable err) {
    if (err instanceof CompletionException && err.getCause() != null) {
      return err.getCause();
    }
    return err;
  }

  @Nested
  class ListEndpoint {

    @Test
    void shouldReturn503WhenOpenFgaUnavailable(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(v -> {
        when(openFgaProvider.get()).thenReturn(null);
        CompletionStage<Response<SuperAdminsListResponse>> cs = resource.list(JWT_CALLER);
        cs.whenComplete((resp, err) -> {
          tc.verify(() -> {
            assertThat(resp).isNull();
            assertThat(err).isNotNull();
            Throwable e = unwrap(err);
            assertThat(e).isInstanceOf(WebApplicationException.class);
            assertThat(((WebApplicationException) e).getResponse().getStatus()).isEqualTo(503);
            verify(jwtService, org.mockito.Mockito.never()).verifyToken(anyString());
          });
          tc.completeNow();
        });
      });
    }

    @Test
    void shouldReturn401WhenJwtSignatureInvalid(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(v -> {
        when(openFgaProvider.get()).thenReturn(openFga);
        when(openFga.isEnabled()).thenReturn(true);
        when(jwtService.isAccessToken("signed-token")).thenReturn(true);
        when(jwtService.verifyToken("signed-token")).thenThrow(new SignatureException("bad sig"));
        CompletionStage<Response<SuperAdminsListResponse>> cs = resource.list(JWT_CALLER);
        cs.whenComplete((resp, err) -> {
          tc.verify(() -> {
            assertThat(resp).isNull();
            assertThat(err).isNotNull();
            Throwable e = unwrap(err);
            assertThat(e).isInstanceOf(WebApplicationException.class);
            assertThat(((WebApplicationException) e).getResponse().getStatus()).isEqualTo(401);
          });
          tc.completeNow();
        });
      });
    }

    @Test
    void shouldReturn401WhenTokenIsNotAccessToken(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(v -> {
        when(openFgaProvider.get()).thenReturn(openFga);
        when(openFga.isEnabled()).thenReturn(true);
        when(jwtService.isAccessToken("signed-token")).thenReturn(false);
        CompletionStage<Response<SuperAdminsListResponse>> cs = resource.list(JWT_CALLER);
        cs.whenComplete((resp, err) -> {
          tc.verify(() -> {
            assertThat(resp).isNull();
            assertThat(err).isNotNull();
            Throwable e = unwrap(err);
            assertThat(e).isInstanceOf(WebApplicationException.class);
            assertThat(((WebApplicationException) e).getResponse().getStatus()).isEqualTo(401);
            verify(jwtService, org.mockito.Mockito.never()).verifyToken(anyString());
            verify(openFga, org.mockito.Mockito.never()).isSuperAdmin(anyString());
          });
          tc.completeNow();
        });
      });
    }

    @Test
    void shouldFailWhenCallerNotSuperadmin(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(v -> {
        withEnabledOpenFga();
        when(openFga.isSuperAdmin("caller-1")).thenReturn(Single.just(false));
        CompletionStage<Response<SuperAdminsListResponse>> cs = resource.list(JWT_CALLER);
        cs.whenComplete((resp, err) -> {
          tc.verify(() -> {
            assertThat(resp).isNull();
            assertThat(err).isNotNull();
          });
          tc.completeNow();
        });
      });
    }

    @Test
    void shouldReturnSortedUserIdsWhenSuperadmin(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(v -> {
        withEnabledOpenFga();
        when(openFga.isSuperAdmin("caller-1")).thenReturn(Single.just(true));
        when(openFga.getSuperAdmins()).thenReturn(Single.just(Set.of("z", "a")));
        CompletionStage<Response<SuperAdminsListResponse>> cs = resource.list(JWT_CALLER);
        cs.whenComplete((resp, err) -> {
          tc.verify(() -> {
            assertThat(err).isNull();
            assertThat(resp).isNotNull();
            assertThat(resp.getData().getUserIds()).containsExactly("a", "z");
            assertThat(resp.getData().getMembers()).hasSize(2);
          });
          tc.completeNow();
        });
      });
    }
  }

  @Nested
  class PostGrant {

    @Test
    void shouldReturn400WhenUserIdAndEmailMissing(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(v -> {
        withEnabledOpenFga();
        GrantSuperAdminRequest body = new GrantSuperAdminRequest();
        body.setUserId("   ");
        body.setEmail("  ");
        CompletionStage<Response<SuperAdminsListResponse>> cs = resource.grant(JWT_CALLER, body);
        cs.whenComplete((resp, err) -> {
          tc.verify(() -> {
            assertThat(err).isNotNull();
            Throwable e = unwrap(err);
            assertThat(e).isInstanceOf(WebApplicationException.class);
            assertThat(((WebApplicationException) e).getResponse().getStatus()).isEqualTo(400);
          });
          tc.completeNow();
        });
      });
    }

    @Test
    void shouldReturn404WhenUserIdDoesNotExistInDb(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(v -> {
        withEnabledOpenFga();
        when(userService.getUsersByIds(List.of("missing-user"))).thenReturn(Single.just(List.of()));
        GrantSuperAdminRequest body = new GrantSuperAdminRequest();
        body.setUserId("missing-user");
        CompletionStage<Response<SuperAdminsListResponse>> cs = resource.grant(JWT_CALLER, body);
        cs.whenComplete((resp, err) -> {
          tc.verify(() -> {
            assertThat(resp).isNull();
            assertThat(err).isNotNull();
            Throwable e = unwrap(err);
            assertThat(e).isInstanceOf(WebApplicationException.class);
            assertThat(((WebApplicationException) e).getResponse().getStatus()).isEqualTo(404);
            verify(openFga, org.mockito.Mockito.never()).assignSuperAdmin(anyString());
          });
          tc.completeNow();
        });
      });
    }
  }

  @Nested
  class DeleteRevoke {

    @Test
    void shouldReturn404WhenTargetNotSuperadmin(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(v -> {
        withEnabledOpenFga();
        when(openFga.isSuperAdmin("caller-1")).thenReturn(Single.just(true));
        when(openFga.getSuperAdmins()).thenReturn(Single.just(Set.of("other")));
        CompletionStage<Response<SuperAdminsListResponse>> cs = resource.revoke(JWT_CALLER, "missing-user");
        cs.whenComplete((resp, err) -> {
          tc.verify(() -> {
            Throwable e = unwrap(err);
            assertThat(e).isInstanceOf(WebApplicationException.class);
            assertThat(((WebApplicationException) e).getResponse().getStatus()).isEqualTo(404);
          });
          tc.completeNow();
        });
      });
    }

    @Test
    void shouldReturn400WhenLastSuperadmin(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(v -> {
        withEnabledOpenFga();
        when(openFga.isSuperAdmin("caller-1")).thenReturn(Single.just(true));
        when(openFga.getSuperAdmins()).thenReturn(Single.just(Set.of("only-one")));
        CompletionStage<Response<SuperAdminsListResponse>> cs = resource.revoke(JWT_CALLER, "only-one");
        cs.whenComplete((resp, err) -> {
          tc.verify(() -> {
            Throwable e = unwrap(err);
            assertThat(e).isInstanceOf(WebApplicationException.class);
            assertThat(((WebApplicationException) e).getResponse().getStatus()).isEqualTo(400);
          });
          tc.completeNow();
        });
      });
    }
  }
}
