package org.dreamhorizon.pulseserver.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

  @Mock
  private ContainerRequestContext requestContext;

  @Mock
  private ContainerResponseContext responseContext;

  @Mock
  private UriInfo uriInfo;

  @Mock
  private JwtService jwtService;

  @Mock
  private Claims claims;

  private TenantFilter tenantFilter;

  @BeforeEach
  void setUp() {
    tenantFilter = new TenantFilter();
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  class RequestFilterTests {

    @Test
    void shouldSetTenantIdFromHeader() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      // No Authorization header, so it falls back to X-API-KEY header
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("test-tenant_secret-key");

      tenantFilter.filter(requestContext);

      assertEquals("test-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldTrimTenantIdFromHeader() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("  test-tenant_secret  ");

      tenantFilter.filter(requestContext);

      assertEquals("test-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldAbortWhenTenantIdMissing() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn(null);

      tenantFilter.filter(requestContext);

      // Request should be aborted when no tenant ID is found
      verify(requestContext).abortWith(any());
      assertNull(TenantContext.getTenantId());
    }

    @Test
    void shouldAbortWhenTenantIdBlank() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("   ");

      tenantFilter.filter(requestContext);

      // Request should be aborted when tenant ID is blank
      verify(requestContext).abortWith(any());
      assertNull(TenantContext.getTenantId());
    }
  }

  @Nested
  class JwtTokenTests {

    @Test
    void shouldExtractTenantIdFromJwtToken() throws IOException {
      tenantFilter.setJwtService(jwtService);
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(claims.get("tenantId", String.class)).thenReturn("jwt-tenant");

      tenantFilter.filter(requestContext);

      assertEquals("jwt-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldTrimTenantIdFromJwtToken() throws IOException {
      tenantFilter.setJwtService(jwtService);
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(claims.get("tenantId", String.class)).thenReturn("  jwt-tenant  ");

      tenantFilter.filter(requestContext);

      assertEquals("jwt-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldFallbackToHeaderWhenTokenHasNoTenantId() throws IOException {
      tenantFilter.setJwtService(jwtService);
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(claims.get("tenantId", String.class)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("header-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("header-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldFallbackToHeaderWhenTokenHasBlankTenantId() throws IOException {
      tenantFilter.setJwtService(jwtService);
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(claims.get("tenantId", String.class)).thenReturn("   ");
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("header-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("header-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldFallbackToHeaderWhenTokenVerificationFails() throws IOException {
      tenantFilter.setJwtService(jwtService);
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer invalid-token");
      when(jwtService.verifyToken("invalid-token")).thenThrow(new RuntimeException("Invalid token"));
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("header-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("header-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldFallbackToHeaderWhenAuthHeaderNotBearer() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Basic some-credentials");
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("header-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("header-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldFallbackToHeaderWhenBearerTokenIsEmpty() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer ");
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("header-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("header-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldFallbackToHeaderWhenBearerTokenIsWhitespace() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer    ");
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("header-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("header-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldAbortWhenNoTenantFromTokenOrHeader() throws IOException {
      tenantFilter.setJwtService(jwtService);
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(claims.get("tenantId", String.class)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn(null);

      tenantFilter.filter(requestContext);

      verify(requestContext).abortWith(any());
      assertNull(TenantContext.getTenantId());
    }
  }

  @Nested
  class ExcludedPathTests {

    @Test
    void shouldSkipFilterForHealthcheckPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("healthcheck");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForHealthcheckPathWithLeadingSlash() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/healthcheck");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForHealthcheckSubPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("healthcheck/deep");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForAuthPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/auth/social/authenticate");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForAuthPathWithLeadingSlash() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/auth/token/verify");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForAlertsPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("alerts");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldSkipFilterForAlertsSubPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/alerts/some/path");

      tenantFilter.filter(requestContext);

      assertNull(TenantContext.getTenantId());
      verify(requestContext, never()).getHeaderString(TenantFilter.API_KEY_HEADER);
    }

    @Test
    void shouldNotExcludeNullPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn(null);
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("test-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("test-tenant", TenantContext.getTenantId());
    }
  }

  @Nested
  class ResponseFilterTests {

    @Test
    void shouldClearTenantContextOnResponse() throws IOException {
      TenantContext.setTenantId("test-tenant");
      assertEquals("test-tenant", TenantContext.getTenantId());

      tenantFilter.filter(requestContext, responseContext);

      assertNull(TenantContext.getTenantId());
    }

    @Test
    void shouldClearTenantContextEvenWhenAlreadyNull() throws IOException {
      assertNull(TenantContext.getTenantId());

      tenantFilter.filter(requestContext, responseContext);

      assertNull(TenantContext.getTenantId());
    }
  }

  @Nested
  class JwtServiceNotAvailableTests {

    @Test
    void shouldFallbackToHeaderWhenJwtServiceIsNull() throws IOException {
      // Don't set jwtService - getJwtService() will try GuiceInjector and likely fail
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer some-token");
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("fallback-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("fallback-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldAbortWhenJwtServiceNullAndNoHeader() throws IOException {
      // Don't set jwtService - getJwtService() will try GuiceInjector and likely fail
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer some-token");
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn(null);

      tenantFilter.filter(requestContext);

      verify(requestContext).abortWith(any());
      assertNull(TenantContext.getTenantId());
    }

    @Test
    void shouldSetJwtServiceViaSetterMethod() throws IOException {
      tenantFilter.setJwtService(jwtService);
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(claims.get("tenantId", String.class)).thenReturn("set-tenant");

      tenantFilter.filter(requestContext);

      assertEquals("set-tenant", TenantContext.getTenantId());
    }
  }

  @Nested
  class EdgeCaseTests {

    @Test
    void shouldHandlePathWithOnlySlash() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("root-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("root-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldHandleEmptyPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("empty-path-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("empty-path-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldNotExcludeHealthcheckPrefix() throws IOException {
      // "healthchecker" should NOT be excluded (it's not exactly "healthcheck")
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("healthchecker");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("health-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("health-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldHandleRegularApiPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/metrics/query");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("api-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("api-tenant", TenantContext.getTenantId());
    }

    @Test
    void shouldProcessNonAuthPathStartingWithV1() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/configs");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("config-tenant_secret");

      tenantFilter.filter(requestContext);

      assertEquals("config-tenant", TenantContext.getTenantId());
    }
  }

  @Nested
  class ProjectIdExtractionTests {

    @Test
    void shouldExtractProjectIdWithSingleUnderscore() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("project123_secret456");

      tenantFilter.filter(requestContext);

      assertEquals("project123", TenantContext.getTenantId());
    }

    @Test
    void shouldExtractProjectIdWithMultipleUnderscores() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("test_project-XwzBrFCb_fYJmt8hy0wmZcXvDq3DGRn7x");

      tenantFilter.filter(requestContext);

      assertEquals("test_project-XwzBrFCb", TenantContext.getTenantId());
    }

    @Test
    void shouldExtractProjectIdWithHyphensInProjectId() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("my-project-id_secret-key-123");

      tenantFilter.filter(requestContext);

      assertEquals("my-project-id", TenantContext.getTenantId());
    }

    @Test
    void shouldExtractProjectIdWithUnderscoreAtEnd() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("project_");

      tenantFilter.filter(requestContext);

      assertEquals("project", TenantContext.getTenantId());
    }

    @Test
    void shouldAbortWhenApiKeyHasNoUnderscore() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("simpleid");

      tenantFilter.filter(requestContext);

      verify(requestContext).abortWith(any());
      assertNull(TenantContext.getTenantId());
    }

    @Test
    void shouldAbortWhenApiKeyIsNull() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn(null);

      tenantFilter.filter(requestContext);

      verify(requestContext).abortWith(any());
      assertNull(TenantContext.getTenantId());
    }

    @Test
    void shouldAbortWhenApiKeyIsBlank() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("   ");

      tenantFilter.filter(requestContext);

      verify(requestContext).abortWith(any());
      assertNull(TenantContext.getTenantId());
    }

    @Test
    void shouldExtractProjectIdWithOnlyUnderscore() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("_secret");

      tenantFilter.filter(requestContext);

      assertEquals("", TenantContext.getTenantId());
    }

    @Test
    void shouldExtractProjectIdWithComplexFormat() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("tenant-123_project-456_xyz789_secret");

      tenantFilter.filter(requestContext);

      assertEquals("tenant-123_project-456_xyz789", TenantContext.getTenantId());
    }

    @Test
    void shouldTrimApiKeyBeforeExtraction() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/v1/some/path");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
      when(requestContext.getHeaderString(TenantFilter.API_KEY_HEADER)).thenReturn("  project123_secret  ");

      tenantFilter.filter(requestContext);

      assertEquals("project123", TenantContext.getTenantId());
    }
  }
}


