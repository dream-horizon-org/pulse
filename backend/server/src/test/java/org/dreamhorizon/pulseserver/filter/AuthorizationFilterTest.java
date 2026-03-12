package org.dreamhorizon.pulseserver.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.lang.reflect.Field;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.junit.jupiter.api.AfterEach;
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
class AuthorizationFilterTest {

  @Mock
  ContainerRequestContext requestContext;

  @Mock
  UriInfo uriInfo;

  @Mock
  OpenFgaService openFgaService;

  @Mock
  JwtService jwtService;

  AuthorizationFilter filter;

  @BeforeEach
  void setUp() throws Exception {
    filter = new AuthorizationFilter();
    injectDependencies();
    ProjectContext.clear();
  }

  @AfterEach
  void tearDown() {
    ProjectContext.clear();
  }

  private void injectDependencies() throws Exception {
    setField(filter, "openFgaService", openFgaService);
    setField(filter, "jwtService", jwtService);
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = AuthorizationFilter.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  @Nested
  class ExcludedPaths {

    @Test
    void shouldSkipAuthorizationForHealthcheck() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("healthcheck");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForAuthPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/auth/login");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForOnboardingPath() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/onboarding/step1");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForHealthcheckWithSlash() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("healthcheck/");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForPathStartingWithHealthcheck() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("healthcheck/ready");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForPathWithLeadingSlash() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("/healthcheck");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }
  }

  @Nested
  class NoProjectContext {

    @Test
    void shouldSkipWhenProjectContextIsNull() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects");
      ProjectContext.clear();

      filter.filter(requestContext);

      verify(openFgaService, never()).checkPermission(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldSkipWhenProjectContextIsBlank() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects");
      ProjectContext.setProjectId("   ");

      filter.filter(requestContext);

      verify(openFgaService, never()).checkPermission(anyString(), anyString(), anyString(), anyString());
    }
  }

  @Nested
  class PermissionChecks {

    @Test
    void shouldAbortUnauthorizedWhenNoUserIdInToken() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer invalid-token");
      when(jwtService.verifyToken("invalid-token")).thenThrow(new RuntimeException("Invalid token"));

      filter.filter(requestContext);

      verify(requestContext).abortWith(any(Response.class));
      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(requestContext).abortWith(captor.capture());
      org.junit.jupiter.api.Assertions.assertEquals(401, captor.getValue().getStatus());
    }

    @Test
    void shouldAbortUnauthorizedWhenNoAuthHeader() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);

      filter.filter(requestContext);

      verify(requestContext).abortWith(any(Response.class));
      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(requestContext).abortWith(captor.capture());
      org.junit.jupiter.api.Assertions.assertEquals(401, captor.getValue().getStatus());
    }

    @Test
    void shouldGrantAccessWhenPermissionCheckReturnsTrue() throws Exception {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      when(requestContext.getMethod()).thenReturn("GET");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(openFgaService.checkPermission(eq("user1"), eq("can_view"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldAbortForbiddenWhenPermissionCheckReturnsFalse() throws Exception {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      when(requestContext.getMethod()).thenReturn("GET");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(openFgaService.checkPermission(eq("user1"), eq("can_view"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(false));

      filter.filter(requestContext);

      verify(requestContext).abortWith(any(Response.class));
      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(requestContext).abortWith(captor.capture());
      org.junit.jupiter.api.Assertions.assertEquals(403, captor.getValue().getStatus());
    }
  }

  @Nested
  class HttpMethodToActionMapping {

    @Test
    void shouldUseCanViewForGetRequest() throws Exception {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      when(requestContext.getMethod()).thenReturn("GET");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(openFgaService.checkPermission(eq("user1"), eq("can_view"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(openFgaService).checkPermission("user1", "can_view", "project", "proj_123");
    }

    @Test
    void shouldUseCanEditForPostRequest() throws Exception {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      when(requestContext.getMethod()).thenReturn("POST");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(openFgaService.checkPermission(eq("user1"), eq("can_edit"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(openFgaService).checkPermission("user1", "can_edit", "project", "proj_123");
    }

    @Test
    void shouldUseCanDeleteProjectForDeleteRequest() throws Exception {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      when(requestContext.getMethod()).thenReturn("DELETE");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(openFgaService.checkPermission(eq("user1"), eq("can_delete_project"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(openFgaService).checkPermission("user1", "can_delete_project", "project", "proj_123");
    }

    @Test
    void shouldUseCanEditForPutRequest() throws Exception {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      when(requestContext.getMethod()).thenReturn("PUT");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(openFgaService.checkPermission(eq("user1"), eq("can_edit"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(openFgaService).checkPermission("user1", "can_edit", "project", "proj_123");
    }

    @Test
    void shouldUseCanEditForPatchRequest() throws Exception {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      when(requestContext.getMethod()).thenReturn("PATCH");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(openFgaService.checkPermission(eq("user1"), eq("can_edit"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(openFgaService).checkPermission("user1", "can_edit", "project", "proj_123");
    }

    @Test
    void shouldUseCanViewForHeadRequest() throws Exception {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      when(requestContext.getMethod()).thenReturn("HEAD");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(openFgaService.checkPermission(eq("user1"), eq("can_view"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(openFgaService).checkPermission("user1", "can_view", "project", "proj_123");
    }

    @Test
    void shouldUseCanViewForTraceMethod() throws Exception {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      when(requestContext.getMethod()).thenReturn("TRACE");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(openFgaService.checkPermission(eq("user1"), eq("can_view"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(openFgaService).checkPermission("user1", "can_view", "project", "proj_123");
    }
  }

  @Nested
  class PermissionCheckFailure {

    @Test
    void shouldAbortInternalErrorWhenPermissionCheckThrows() throws Exception {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      when(requestContext.getMethod()).thenReturn("GET");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-token");
      io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
      when(claims.getSubject()).thenReturn("user1");
      when(jwtService.verifyToken("valid-token")).thenReturn(claims);
      when(openFgaService.checkPermission(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(Single.error(new RuntimeException("OpenFGA connection failed")));

      filter.filter(requestContext);

      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(requestContext).abortWith(captor.capture());
      org.junit.jupiter.api.Assertions.assertEquals(500, captor.getValue().getStatus());
    }
  }

  @Nested
  class TokenExtraction {

    @Test
    void shouldAbortWhenBearerPrefixWithEmptyToken() throws IOException {
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(uriInfo.getPath()).thenReturn("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer ");

      filter.filter(requestContext);

      verify(requestContext).abortWith(any(Response.class));
      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(requestContext).abortWith(captor.capture());
      org.junit.jupiter.api.Assertions.assertEquals(401, captor.getValue().getStatus());
    }
  }
}
