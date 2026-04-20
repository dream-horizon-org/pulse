package org.dreamhorizon.pulseserver.resources.usagelimits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.UsageCreditsRedisSyncRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.kong.KongUsageCreditsRedisSyncService;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalUsageLimitsControllerTest {

  @Mock
  private UsageLimitService usageLimitService;
  @Mock
  private KongUsageCreditsRedisSyncService kongUsageCreditsRedisSyncService;
  @Mock
  private JwtService jwtService;

  private InternalUsageLimitsController controller;

  @BeforeEach
  void setUp() {
    controller = new InternalUsageLimitsController(
        usageLimitService, kongUsageCreditsRedisSyncService, jwtService);
  }

  @Test
  void shouldReturnSyncSummaryWhenUsageCreditsSyncSucceeds() throws Exception {
    when(kongUsageCreditsRedisSyncService.syncUsageCreditsToRedis()).thenReturn(Single.just(12));

    Response<UsageCreditsRedisSyncRestResponse> response =
        controller.syncUsageCreditsToRedis().toCompletableFuture().get();

    assertThat(response.getData().getProjectsSynced()).isEqualTo(12);
    assertThat(response.getData().getDurationMs()).isGreaterThanOrEqualTo(0L);
  }
}
