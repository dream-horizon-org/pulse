package org.dreamhorizon.pulseserver.resources.v1.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import io.jsonwebtoken.Claims;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.WebApplicationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.rest.exception.ForbiddenOperationException;
import org.dreamhorizon.pulseserver.dto.request.ReqUserInfo;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.CreateAdminTenantRequest;
import org.dreamhorizon.pulseserver.resources.v1.admin.models.CreateAdminTenantResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.UserService;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;
import org.dreamhorizon.pulseserver.service.tenant.dto.TenantWithProjectResult;
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
class AdminTenantsControllerTest {

  @Mock
  private TenantService tenantService;

  @Mock
  private JwtService jwtService;

  @Mock
  private Provider<OpenFgaService> openFgaServiceProvider;

  @Mock
  private OpenFgaService openFgaService;

  @Mock
  private UserService userService;

  @Mock
  private Claims claims;

  private AdminTenantsController controller;

  private static final String VALID_TOKEN = "valid.jwt.token";
  private static final String BEARER_TOKEN = "Bearer " + VALID_TOKEN;
  private static final String CALLER_ID = "caller-user-1";

  @BeforeEach
  void setUp() {
    controller = new AdminTenantsController(tenantService, jwtService, openFgaServiceProvider, userService);
    when(openFgaServiceProvider.get()).thenReturn(openFgaService);
    when(openFgaService.isEnabled()).thenReturn(true);
    when(jwtService.isAccessToken(VALID_TOKEN)).thenReturn(true);
    when(jwtService.verifyToken(VALID_TOKEN)).thenReturn(claims);
    when(claims.getSubject()).thenReturn(CALLER_ID);
  }

  private <T> Response<T> await(CompletionStage<Response<T>> stage) {
    try {
      return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private static Throwable unwrap(Throwable err) {
    if (err instanceof CompletionException && err.getCause() != null) {
      return err.getCause();
    }
    if (err instanceof ExecutionException && err.getCause() != null) {
      return err.getCause();
    }
    return err;
  }

  private CreateAdminTenantRequest validRequest() {
    CreateAdminTenantRequest req = new CreateAdminTenantRequest();
    req.setTenantName("Test Org");
    req.setProjectName("Test Project");
    req.setDescription("A description");
    return req;
  }

  private User callerUser() {
    return User.builder()
        .userId(CALLER_ID)
        .email("admin@example.com")
        .name("Admin User")
        .isActive(true)
        .build();
  }

  private TenantWithProjectResult successResult() {
    TenantWithProjectResult r = new TenantWithProjectResult();
    r.setTenantId("tenant-abc-123");
    r.setProjectId("test-project-xyz1");
    r.setRawApiKey("raw-api-key");
    return r;
  }

  @Nested
  class CreateTenantWithProject {

    @Test
    void shouldReturn200ForSuperadmin() {
      when(openFgaService.isSuperAdmin(CALLER_ID)).thenReturn(Single.just(true));
      when(openFgaService.isInternalViewer(CALLER_ID)).thenReturn(Single.just(false));
      when(userService.getUserById(CALLER_ID)).thenReturn(Single.just(callerUser()));
      when(tenantService.createTenantWithProject(any(ReqUserInfo.class), eq("Test Org"), eq("Test Project"), any()))
          .thenReturn(Single.just(successResult()));

      Response<CreateAdminTenantResponse> response =
          await(controller.createTenantWithProject(BEARER_TOKEN, validRequest()));

      assertThat(response).isNotNull();
      assertThat(response.getData()).isNotNull();
      assertThat(response.getData().getTenantId()).isEqualTo("tenant-abc-123");
      assertThat(response.getData().getProjectId()).isEqualTo("test-project-xyz1");
      assertThat(response.getData().getApiKey()).isEqualTo("raw-api-key");
    }

    @Test
    void shouldReturn200ForInternalViewer() {
      when(openFgaService.isSuperAdmin(CALLER_ID)).thenReturn(Single.just(false));
      when(openFgaService.isInternalViewer(CALLER_ID)).thenReturn(Single.just(true));
      when(userService.getUserById(CALLER_ID)).thenReturn(Single.just(callerUser()));
      when(tenantService.createTenantWithProject(any(ReqUserInfo.class), any(), any(), any()))
          .thenReturn(Single.just(successResult()));

      Response<CreateAdminTenantResponse> response =
          await(controller.createTenantWithProject(BEARER_TOKEN, validRequest()));

      assertThat(response).isNotNull();
      assertThat(response.getData()).isNotNull();
      assertThat(response.getData().getTenantId()).isEqualTo("tenant-abc-123");
    }

    @Test
    void shouldReturn403ForNonSystemRoleUser() {
      when(openFgaService.isSuperAdmin(CALLER_ID)).thenReturn(Single.just(false));
      when(openFgaService.isInternalViewer(CALLER_ID)).thenReturn(Single.just(false));

      ExecutionException ex = assertThrows(ExecutionException.class, () ->
          controller.createTenantWithProject(BEARER_TOKEN, validRequest())
              .toCompletableFuture()
              .get(5, TimeUnit.SECONDS));

      Throwable cause = unwrap(ex.getCause());
      assertThat(cause).isInstanceOf(ForbiddenOperationException.class);
      assertThat(cause.getMessage()).contains("superadmin or internal_viewer");
    }

    @Test
    void shouldReturn400WhenTenantNameMissing() {
      when(openFgaService.isSuperAdmin(CALLER_ID)).thenReturn(Single.just(true));
      when(openFgaService.isInternalViewer(CALLER_ID)).thenReturn(Single.just(false));

      CreateAdminTenantRequest req = new CreateAdminTenantRequest();
      req.setProjectName("Test Project");

      ExecutionException ex = assertThrows(ExecutionException.class, () ->
          controller.createTenantWithProject(BEARER_TOKEN, req)
              .toCompletableFuture()
              .get(5, TimeUnit.SECONDS));

      Throwable cause = unwrap(ex.getCause());
      assertThat(cause).isInstanceOf(WebApplicationException.class);
      assertThat(((WebApplicationException) cause).getResponse().getStatus()).isEqualTo(400);
    }
  }
}
