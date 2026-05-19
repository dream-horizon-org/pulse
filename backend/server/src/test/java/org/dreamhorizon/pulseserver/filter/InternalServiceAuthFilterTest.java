package org.dreamhorizon.pulseserver.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.util.List;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalServiceAuthFilterTest {

  @Mock
  ContainerRequestContext requestContext;

  @Mock
  UriInfo uriInfo;

  @Mock
  ApplicationConfig applicationConfig;

  InternalServiceAuthFilter filter;

  @BeforeEach
  void setUp() throws Exception {
    filter = new InternalServiceAuthFilter();
    injectConfig(applicationConfig);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
  }

  private void injectConfig(ApplicationConfig config) throws Exception {
    Field field = InternalServiceAuthFilter.class.getDeclaredField("applicationConfig");
    field.setAccessible(true);
    field.set(filter, config);
  }

  private void setupPath(String path) {
    when(uriInfo.getPath()).thenReturn(path);
  }

  private int captureAbortStatus() {
    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    verify(requestContext).abortWith(captor.capture());
    return captor.getValue().getStatus();
  }

  @Nested
  class NonInternalPaths {

    @Test
    void shouldBeNoOpForNonInternalPath() {
      setupPath("v1/projects/abc");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext, never()).setProperty(any(), any());
    }

    @Test
    void shouldBeNoOpForHealthcheckPath() {
      setupPath("healthcheck");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext, never()).setProperty(any(), any());
    }

    @Test
    void shouldBeNoOpForAuthPath() {
      setupPath("v1/auth/login");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext, never()).setProperty(any(), any());
    }

    @Test
    void shouldBeNoOpForPathWithLeadingSlashThatIsNotInternal() {
      setupPath("/v1/analytics/data");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext, never()).setProperty(any(), any());
    }

    @Test
    void shouldBeNoOpForInternalProjectLimitsPathNotInCronSet() {
      setupPath("internal/v1/projects/proj1/limits");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext, never()).setProperty(any(), any());
    }

    @Test
    void shouldBeNoOpForInternalProjectLimitsResetPathNotInCronSet() {
      setupPath("internal/v1/projects/proj1/limits/reset");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext, never()).setProperty(any(), any());
    }

    @Test
    void shouldBeNoOpForInternalPathNotInCronSetWithLeadingSlash() {
      setupPath("/internal/v1/projects/proj1/limits");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext, never()).setProperty(any(), any());
    }
  }

  @Nested
  class DevMode {

    @Test
    void shouldAutoAllowInternalPathWhenGoogleOauthDisabled() {
      setupPath("internal/v1/api-keys/sync-to-redis");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext).setProperty(InternalServiceAuthFilter.PROP_INTERNAL_AUTHENTICATED, true);
    }

    @Test
    void shouldAutoAllowWhenGoogleOauthEnabledIsNull() {
      setupPath("internal/v1/projects/limits/sync-to-redis");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(null);

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext).setProperty(InternalServiceAuthFilter.PROP_INTERNAL_AUTHENTICATED, true);
    }

    @Test
    void shouldAutoAllowWithLeadingSlashWhenDevMode() {
      setupPath("/internal/analytics/funnels");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext).setProperty(InternalServiceAuthFilter.PROP_INTERNAL_AUTHENTICATED, true);
    }
  }

  @Nested
  class ValidToken {

    @Test
    void shouldAuthenticateWhenTokenMatchesConfiguredToken() {
      setupPath("internal/v1/api-keys/sync-to-redis");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getInternalServiceTokenList()).thenReturn(List.of("secret-token-abc"));
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
          .thenReturn("Bearer secret-token-abc");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext).setProperty(InternalServiceAuthFilter.PROP_INTERNAL_AUTHENTICATED, true);
    }

    @Test
    void shouldAuthenticateWhenTokenMatchesOneOfMultipleConfiguredTokens() {
      setupPath("internal/v1/projects/limits/sync-to-redis");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getInternalServiceTokenList())
          .thenReturn(List.of("token-one", "token-two", "token-three"));
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
          .thenReturn("Bearer token-two");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext).setProperty(InternalServiceAuthFilter.PROP_INTERNAL_AUTHENTICATED, true);
    }
  }

  @Nested
  class InvalidToken {

    @Test
    void shouldRejectWhenTokenDoesNotMatchAnyConfiguredToken() {
      setupPath("internal/analytics/funnels");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getInternalServiceTokenList()).thenReturn(List.of("correct-token"));
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
          .thenReturn("Bearer wrong-token");

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(401);
      verify(requestContext, never()).setProperty(any(), any());
    }

    @Test
    void shouldRejectWhenNoTokensAreConfigured() {
      setupPath("internal/v1/projects/limits/sync-to-redis");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getInternalServiceTokenList()).thenReturn(List.of());
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
          .thenReturn("Bearer some-token");

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(401);
      verify(requestContext, never()).setProperty(any(), any());
    }
  }

  @Nested
  class MissingHeader {

    @Test
    void shouldRejectWhenAuthorizationHeaderIsAbsent() {
      setupPath("internal/v1/projects/limits/process-usage-notifications");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getInternalServiceTokenList()).thenReturn(List.of("valid-token"));
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(401);
      verify(requestContext, never()).setProperty(any(), any());
    }

    @Test
    void shouldRejectWhenAuthorizationHeaderIsNotBearer() {
      setupPath("internal/analytics/journeys");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getInternalServiceTokenList()).thenReturn(List.of("valid-token"));
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
          .thenReturn("Basic dXNlcjpwYXNz");

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(401);
      verify(requestContext, never()).setProperty(any(), any());
    }

    @Test
    void shouldRejectWhenAuthorizationHeaderIsBearerWithEmptyToken() {
      setupPath("internal/analytics/events");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getInternalServiceTokenList()).thenReturn(List.of("valid-token"));
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer ");

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(401);
      verify(requestContext, never()).setProperty(any(), any());
    }
  }

  @Nested
  class UserJwtFallback {

    @Mock
    JwtService jwtService;

    @BeforeEach
    void injectJwtService() {
      filter.setJwtService(jwtService);
    }

    @Test
    void shouldAllowValidUserJwtOnCronPath() {
      setupPath("internal/v1/api-keys/sync-to-redis");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getInternalServiceTokenList()).thenReturn(List.of("service-token"));
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
          .thenReturn("Bearer valid.user.jwt");
      when(jwtService.verifyToken("valid.user.jwt")).thenReturn(mock(Claims.class));

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any());
      verify(requestContext, never()).setProperty(any(), any());
    }

    @Test
    void shouldRejectInvalidJwtThatIsAlsoNotServiceToken() {
      setupPath("internal/analytics/funnels");
      when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);
      when(applicationConfig.getInternalServiceTokenList()).thenReturn(List.of("service-token"));
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
          .thenReturn("Bearer not.a.valid.jwt");
      when(jwtService.verifyToken("not.a.valid.jwt"))
          .thenThrow(new RuntimeException("invalid signature"));

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(401);
      verify(requestContext, never()).setProperty(any(), any());
    }
  }
}
