package org.dreamhorizon.pulseserver.resources.tenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.resources.tenants.models.TenantRestResponse;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantRestResponseFactoryTest {

  @Mock
  private TierService tierService;

  private TenantRestResponseFactory factory;

  @BeforeEach
  void setUp() {
    factory = new TenantRestResponseFactory(tierService);
  }

  @Nested
  class ToResponseWithTier {

    @Test
    void shouldResolveEnterpriseWhenTierIdIs2() {
      when(tierService.getTierNameById(eq(2))).thenReturn(Maybe.just("enterprise"));
      Tenant tenant =
          Tenant.builder()
              .tenantId("t1")
              .name("Acme")
              .tierId(2)
              .isActive(true)
              .build();

      TenantRestResponse r = factory.toResponseWithTier(tenant).blockingGet();

      assertThat(r.getTier()).isEqualTo("enterprise");
      assertThat(r.getTenantId()).isEqualTo("t1");
      verify(tierService).getTierNameById(2);
    }

    @Test
    void shouldDefaultToFreeWhenTierRowMissing() {
      when(tierService.getTierNameById(eq(999))).thenReturn(Maybe.empty());
      Tenant tenant =
          Tenant.builder().tenantId("t2").name("B").tierId(999).isActive(true).build();

      TenantRestResponse r = factory.toResponseWithTier(tenant).blockingGet();

      assertThat(r.getTier()).isEqualTo("free");
    }
  }
}
