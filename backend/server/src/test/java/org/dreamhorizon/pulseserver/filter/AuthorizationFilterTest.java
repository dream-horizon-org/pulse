package org.dreamhorizon.pulseserver.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
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

  @Mock
  ResourceInfo resourceInfo;

  AuthorizationFilter filter;

  /**
   * Test helper with method-level annotations for different permissions.
   */
  static class TestResource {
    @RequiresPermission("can_view")
    public void viewEndpoint() {
    }

    @RequiresPermission("can_edit")
    public void editEndpoint() {
    }

    @RequiresPermission("can_delete_project")
    public void deleteEndpoint() {
    }

    public void unannotatedEndpoint() {
    }
  }

  @RequiresPermission("can_view")
  static class ClassAnnotatedResource {
    public void inheritedEndpoint() {
    }
  }

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
    setField(filter, "resourceInfo", resourceInfo);
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = AuthorizationFilter.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private void setupAnnotatedMethod(String permission) throws Exception {
    String methodName = switch (permission) {
      case "can_view" -> "viewEndpoint";
      case "can_edit" -> "editEndpoint";
      case "can_delete_project" -> "deleteEndpoint";
      default -> throw new IllegalArgumentException("Unknown permission: " + permission);
    };
    when(resourceInfo.getResourceMethod())
        .thenReturn(TestResource.class.getMethod(methodName));
  }

  private void setupUnannotatedMethod() throws Exception {
    when(resourceInfo.getResourceMethod())
        .thenReturn(TestResource.class.getMethod("unannotatedEndpoint"));
    when(resourceInfo.getResourceClass())
        .thenReturn((Class) TestResource.class);
  }

  private void setupClassAnnotatedMethod() throws Exception {
    when(resourceInfo.getResourceMethod())
        .thenReturn(ClassAnnotatedResource.class.getMethod("inheritedEndpoint"));
    when(resourceInfo.getResourceClass())
        .thenReturn((Class) ClassAnnotatedResource.class);
  }

  private void setupPath(String path) {
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getPath()).thenReturn(path);
  }

  private void setupValidAuth(String userId) {
    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
        .thenReturn("Bearer valid-token");
    Claims claims = org.mockito.Mockito.mock(Claims.class);
    when(claims.getSubject()).thenReturn(userId);
    when(jwtService.verifyToken("valid-token")).thenReturn(claims);
  }

  private int captureAbortStatus() {
    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    verify(requestContext).abortWith(captor.capture());
    return captor.getValue().getStatus();
  }

  @Nested
  class ExcludedPaths {

    @Test
    void shouldSkipAuthorizationForHealthcheck() throws IOException {
      setupPath("healthcheck");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForAuthPath() throws IOException {
      setupPath("v1/auth/login");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForOnboardingPath() throws IOException {
      setupPath("v1/onboarding/step1");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForHealthcheckWithTrailingSlash() throws IOException {
      setupPath("healthcheck/");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForHealthcheckSubpath() throws IOException {
      setupPath("healthcheck/ready");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForPathWithLeadingSlash() throws IOException {
      setupPath("/healthcheck");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForConfigsPath() throws IOException {
      setupPath("v1/configs/active");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldSkipAuthorizationForSymbolUploadPath() throws IOException {
      setupPath("v1/symbolicate/file/upload");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }
  }

  @Nested
  class NoAnnotation {

    @Test
    void shouldSkipWhenResourceInfoIsNull() throws Exception {
      setField(filter, "resourceInfo", null);
      setupPath("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
      verify(openFgaService, never()).checkPermission(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldSkipWhenMethodHasNoAnnotation() throws Exception {
      setupUnannotatedMethod();
      setupPath("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
      verify(openFgaService, never()).checkPermission(anyString(), anyString(), anyString(), anyString());
    }
  }

  @Nested
  class NoProjectContext {

    @Test
    void shouldSkipWhenProjectContextIsBlank() throws Exception {
      setupAnnotatedMethod("can_view");
      setupPath("v1/projects");
      ProjectContext.setProjectId("   ");

      filter.filter(requestContext);

      verify(openFgaService, never()).checkPermission(anyString(), anyString(), anyString(), anyString());
    }
  }

  @Nested
  class PermissionChecks {

    @Test
    void shouldAbortUnauthorizedWhenNoAuthHeader() throws Exception {
      setupAnnotatedMethod("can_view");
      setupPath("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(401);
    }

    @Test
    void shouldAbortUnauthorizedWhenInvalidToken() throws Exception {
      setupAnnotatedMethod("can_view");
      setupPath("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
          .thenReturn("Bearer invalid-token");
      when(jwtService.verifyToken("invalid-token"))
          .thenThrow(new RuntimeException("Invalid token"));

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(401);
    }

    @Test
    void shouldGrantAccessWhenPermissionCheckReturnsTrue() throws Exception {
      setupAnnotatedMethod("can_view");
      setupPath("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      setupValidAuth("user1");
      when(openFgaService.checkPermission("user1", "can_view", "project", "proj_123"))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void shouldAbortForbiddenWhenPermissionCheckReturnsFalse() throws Exception {
      setupAnnotatedMethod("can_view");
      setupPath("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      setupValidAuth("user1");
      when(openFgaService.checkPermission("user1", "can_view", "project", "proj_123"))
          .thenReturn(Single.just(false));

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(403);
    }
  }

  @Nested
  class AnnotationResolution {

    @Test
    void shouldUseCanViewFromAnnotation() throws Exception {
      setupAnnotatedMethod("can_view");
      setupPath("v1/interactions");
      ProjectContext.setProjectId("proj_123");
      setupValidAuth("user1");
      when(openFgaService.checkPermission(eq("user1"), eq("can_view"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(openFgaService).checkPermission("user1", "can_view", "project", "proj_123");
    }

    @Test
    void shouldUseCanEditFromAnnotation() throws Exception {
      setupAnnotatedMethod("can_edit");
      setupPath("v1/interactions");
      ProjectContext.setProjectId("proj_123");
      setupValidAuth("user1");
      when(openFgaService.checkPermission(eq("user1"), eq("can_edit"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(openFgaService).checkPermission("user1", "can_edit", "project", "proj_123");
    }

    @Test
    void shouldUseCanDeleteProjectFromAnnotation() throws Exception {
      setupAnnotatedMethod("can_delete_project");
      setupPath("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      setupValidAuth("user1");
      when(openFgaService.checkPermission(eq("user1"), eq("can_delete_project"), eq("project"), eq("proj_123")))
          .thenReturn(Single.just(true));

      filter.filter(requestContext);

      verify(openFgaService).checkPermission("user1", "can_delete_project", "project", "proj_123");
    }

    @Test
    void shouldFallBackToClassLevelAnnotation() throws Exception {
      setupClassAnnotatedMethod();
      setupPath("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      setupValidAuth("user1");
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
      setupAnnotatedMethod("can_view");
      setupPath("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      setupValidAuth("user1");
      when(openFgaService.checkPermission(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(Single.error(new RuntimeException("OpenFGA connection failed")));

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(500);
    }
  }

  @Nested
  class TokenExtraction {

    @Test
    void shouldAbortWhenBearerPrefixWithEmptyToken() throws Exception {
      setupAnnotatedMethod("can_view");
      setupPath("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer ");

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(401);
    }

    @Test
    void shouldAbortWhenAuthHeaderIsNotBearer() throws Exception {
      setupAnnotatedMethod("can_view");
      setupPath("v1/projects/proj_123");
      ProjectContext.setProjectId("proj_123");
      when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Basic abc123");

      filter.filter(requestContext);

      assertThat(captureAbortStatus()).isEqualTo(401);
    }
  }
}
