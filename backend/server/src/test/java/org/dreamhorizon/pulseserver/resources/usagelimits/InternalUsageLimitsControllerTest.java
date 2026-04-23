package org.dreamhorizon.pulseserver.resources.usagelimits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.constant.CronJobType;
import org.dreamhorizon.pulseserver.resources.internal.models.CronRedisSyncJobAcceptedRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.cron.CronRedisMaterializationJobService;
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
  private CronRedisMaterializationJobService cronRedisMaterializationJobService;
  @Mock
  private JwtService jwtService;

  private InternalUsageLimitsController controller;

  @BeforeEach
  void setUp() {
    controller = new InternalUsageLimitsController(
        usageLimitService, cronRedisMaterializationJobService, jwtService);
  }

  @Test
  void shouldReturnAcceptedPayloadWhenUsageCreditsEnqueueSucceeds() throws Exception {
    when(cronRedisMaterializationJobService.acceptUsageCreditsSyncToRedis()).thenReturn(
        Single.just(CronRedisSyncJobAcceptedRestResponse.builder()
            .jobId(7L)
            .deduplicated(true)
            .jobType(CronJobType.USAGE_CREDITS_TO_REDIS)
            .build()));

    Response<CronRedisSyncJobAcceptedRestResponse> response =
        controller.syncUsageCreditsToRedis().toCompletableFuture().get();

    assertThat(response.getHttpStatusCode()).isEqualTo(202);
    assertThat(response.getData().getJobId()).isEqualTo(7L);
    assertThat(response.getData().isDeduplicated()).isTrue();
    assertThat(response.getData().getJobType()).isEqualTo(CronJobType.USAGE_CREDITS_TO_REDIS);
  }
}
