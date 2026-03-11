package org.dreamhorizon.pulseserver.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.tenant.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

  @Mock
  TenantDao tenantDao;

  @Mock
  ClickhouseProjectConnectionPoolManager poolManager;

  @Mock
  OpenFgaService openFgaService;

  TenantService tenantService;

  @BeforeEach
  void setUp() {
    tenantService = new TenantService(tenantDao, poolManager, openFgaService);
  }

  private Tenant createTenant(String tenantId, String name) {
    return Tenant.builder()
        .tenantId(tenantId)
        .name(name)
        .description("Test description")
        .tierId(1)
        .isActive(true)
        .build();
  }

  @Nested
  class CreateTenant {

    @Test
    void shouldCreateTenantSuccessfully() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("tenant-1")
          .name("Test Org")
          .description("A test organization")
          .gcpTenantId("gcp-123")
          .domainName("test.com")
          .build();

      Tenant expected = Tenant.builder()
          .tenantId("tenant-1")
          .name("Test Org")
          .description("A test organization")
          .gcpTenantId("gcp-123")
          .domainName("test.com")
          .isActive(true)
          .build();

      when(tenantDao.createTenant(any(Tenant.class))).thenReturn(Single.just(expected));

      Tenant result = tenantService.createTenant(request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTenantId()).isEqualTo("tenant-1");
      assertThat(result.getName()).isEqualTo("Test Org");
      assertThat(result.getIsActive()).isTrue();

      ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
      verify(tenantDao).createTenant(captor.capture());
      assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-1");
      assertThat(captor.getValue().getName()).isEqualTo("Test Org");
      assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    void shouldPropagateErrorFromDao() {
      CreateTenantRequest request = CreateTenantRequest.builder()
          .tenantId("tenant-1")
          .name("Test Org")
          .build();

      when(tenantDao.createTenant(any(Tenant.class)))
          .thenReturn(Single.error(new RuntimeException("DB error")));

      tenantService.createTenant(request)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("DB error"));
    }
  }

  @Nested
  class GetTenant {

    @Test
    void shouldReturnTenantWhenFound() {
      Tenant tenant = createTenant("tenant-1", "Test Org");
      when(tenantDao.getTenantById("tenant-1")).thenReturn(Maybe.just(tenant));

      Tenant result = tenantService.getTenant("tenant-1").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTenantId()).isEqualTo("tenant-1");
      verify(tenantDao).getTenantById("tenant-1");
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
      when(tenantDao.getTenantById("nonexistent")).thenReturn(Maybe.empty());

      tenantService.getTenant("nonexistent")
          .test()
          .assertNoValues()
          .assertComplete();
    }

    @Test
    void shouldPropagateErrorFromDao() {
      when(tenantDao.getTenantById("tenant-1"))
          .thenReturn(Maybe.error(new RuntimeException("DB error")));

      tenantService.getTenant("tenant-1")
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class GetAllActiveTenants {

    @Test
    void shouldReturnActiveTenants() {
      Tenant t1 = createTenant("tenant-1", "Org 1");
      Tenant t2 = createTenant("tenant-2", "Org 2");
      when(tenantDao.getAllActiveTenants()).thenReturn(Flowable.just(t1, t2));

      tenantService.getAllActiveTenants()
          .test()
          .assertValueCount(2)
          .assertValues(t1, t2)
          .assertComplete();
    }

    @Test
    void shouldReturnEmptyWhenNoActiveTenants() {
      when(tenantDao.getAllActiveTenants()).thenReturn(Flowable.empty());

      tenantService.getAllActiveTenants()
          .test()
          .assertNoValues()
          .assertComplete();
    }

    @Test
    void shouldPropagateErrorFromDao() {
      when(tenantDao.getAllActiveTenants())
          .thenReturn(Flowable.error(new RuntimeException("DB error")));

      tenantService.getAllActiveTenants()
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class GetAllTenants {

    @Test
    void shouldReturnAllTenants() {
      Tenant t1 = createTenant("tenant-1", "Org 1");
      when(tenantDao.getAllTenants()).thenReturn(Flowable.just(t1));

      tenantService.getAllTenants()
          .test()
          .assertValueCount(1)
          .assertValue(t1)
          .assertComplete();
    }

    @Test
    void shouldPropagateErrorFromDao() {
      when(tenantDao.getAllTenants())
          .thenReturn(Flowable.error(new RuntimeException("DB error")));

      tenantService.getAllTenants()
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class UpdateTenant {

    @Test
    void shouldUpdateTenantSuccessfully() {
      UpdateTenantRequest request = UpdateTenantRequest.builder()
          .tenantId("tenant-1")
          .name("Updated Org")
          .description("Updated desc")
          .build();

      Tenant updated = Tenant.builder()
          .tenantId("tenant-1")
          .name("Updated Org")
          .description("Updated desc")
          .build();

      when(tenantDao.updateTenant(any(Tenant.class))).thenReturn(Single.just(updated));

      Tenant result = tenantService.updateTenant(request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getName()).isEqualTo("Updated Org");
      verify(tenantDao).updateTenant(any(Tenant.class));
    }

    @Test
    void shouldPropagateErrorFromDao() {
      UpdateTenantRequest request = UpdateTenantRequest.builder()
          .tenantId("tenant-1")
          .name("Updated")
          .build();

      when(tenantDao.updateTenant(any(Tenant.class)))
          .thenReturn(Single.error(new RuntimeException("DB error")));

      tenantService.updateTenant(request)
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class DeactivateTenant {

    @Test
    void shouldDeactivateTenantSuccessfully() {
      when(tenantDao.deactivateTenant("tenant-1")).thenReturn(Completable.complete());

      tenantService.deactivateTenant("tenant-1").blockingAwait();

      verify(tenantDao).deactivateTenant("tenant-1");
    }

    @Test
    void shouldPropagateErrorFromDao() {
      when(tenantDao.deactivateTenant("tenant-1"))
          .thenReturn(Completable.error(new RuntimeException("DB error")));

      tenantService.deactivateTenant("tenant-1")
          .test()
          .assertError(RuntimeException.class);
    }
  }

  @Nested
  class ActivateTenant {

    @Test
    void shouldActivateTenantSuccessfully() {
      when(tenantDao.activateTenant("tenant-1")).thenReturn(Completable.complete());

      tenantService.activateTenant("tenant-1").blockingAwait();

      verify(tenantDao).activateTenant("tenant-1");
    }

    @Test
    void shouldPropagateErrorFromDao() {
      when(tenantDao.activateTenant("tenant-1"))
          .thenReturn(Completable.error(new RuntimeException("DB error")));

      tenantService.activateTenant("tenant-1")
          .test()
          .assertError(RuntimeException.class);
    }
  }
}
