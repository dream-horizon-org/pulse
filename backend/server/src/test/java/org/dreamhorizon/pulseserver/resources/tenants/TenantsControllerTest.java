package org.dreamhorizon.pulseserver.resources.tenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import io.jsonwebtoken.Claims;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.Maybe;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.resources.tenants.models.CreateInternalTenantRestRequest;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantRestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;
import org.dreamhorizon.pulseserver.service.tier.TierService;
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
class TenantsControllerTest {

  @Mock
  private TenantService tenantService;

  @Mock
  private JwtService jwtService;

  @Mock
  private Provider<OpenFgaService> openFgaServiceProvider;

  @Mock
  private OpenFgaService openFgaService;

  @Mock
  private Claims claims;

  @Mock
  private TierService tierService;

  private TenantRestResponseFactory tenantRestResponseFactory;

  private TenantsController controller;

  @BeforeEach
  void setUp() {
    tenantRestResponseFactory = new TenantRestResponseFactory(tierService);
    controller =
        new TenantsController(
            tenantService, jwtService, openFgaServiceProvider, tenantRestResponseFactory);
    when(openFgaServiceProvider.get()).thenReturn(openFgaService);
    when(tierService.getTierNameById(any())).thenReturn(Maybe.just("free"));
  }

  private <T> Response<T> await(CompletionStage<Response<T>> stage) {
    try {
      return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  @Nested
  class CreateTenant {

    @Test
    void shouldCreateTenantAndAssignCreatorAsAdmin() {
      String userId = "user-1";
      String tenantName = "Test Tenant";
      String authorization = "Bearer test-token";

      when(jwtService.isAccessToken("test-token")).thenReturn(true);
      when(jwtService.verifyToken("test-token")).thenReturn(claims);
      when(claims.getSubject()).thenReturn(userId);
      when(openFgaService.isEnabled()).thenReturn(true);
      when(openFgaService.isSuperAdmin(userId)).thenReturn(Single.just(true));
      when(openFgaService.isInternalViewer(userId)).thenReturn(Single.just(false));

      Tenant createdTenant = Tenant.builder()
          .tenantId("tenant-123")
          .name(tenantName)
          .description("Test Description")
          .tierId(1)
          .isActive(true)
          .build();

      when(tenantService.createTenant(org.mockito.ArgumentMatchers.any()))
          .thenReturn(Single.just(createdTenant));
      when(openFgaService.assignTenantRole(userId, "tenant-123", "admin"))
          .thenReturn(Completable.complete());

      CreateInternalTenantRestRequest request = new CreateInternalTenantRestRequest();
      request.setName(tenantName);
      request.setDescription("Test Description");

      Response<TenantRestResponse> response = await(controller.createTenant(authorization, request));

      assertThat(response).isNotNull();
      assertThat(response.getData()).isNotNull();
      assertThat(response.getData().getTenantId()).isEqualTo("tenant-123");
      assertThat(response.getData().getName()).isEqualTo(tenantName);
      assertThat(response.getData().getTier()).isEqualTo("free");

      verify(openFgaService).assignTenantRole(userId, "tenant-123", "admin");
    }

    @Test
    void shouldFailIfNotSuperAdminOrInternalViewer() {
      String userId = "user-1";
      String authorization = "Bearer test-token";

      when(jwtService.isAccessToken("test-token")).thenReturn(true);
      when(jwtService.verifyToken("test-token")).thenReturn(claims);
      when(claims.getSubject()).thenReturn(userId);
      when(openFgaService.isEnabled()).thenReturn(true);
      when(openFgaService.isSuperAdmin(userId)).thenReturn(Single.just(false));
      when(openFgaService.isInternalViewer(userId)).thenReturn(Single.just(false));

      CreateInternalTenantRestRequest request = new CreateInternalTenantRestRequest();
      request.setName("Test Tenant");

      assertThrows(Exception.class, () -> {
        await(controller.createTenant(authorization, request));
      });
    }

    @Test
    void shouldFailIfMissingAuthorizationHeader() {
      CreateInternalTenantRestRequest request = new CreateInternalTenantRestRequest();
      request.setName("Test Tenant");

      assertThrows(Exception.class, () -> {
        await(controller.createTenant(null, request));
      });
    }
  }
}
