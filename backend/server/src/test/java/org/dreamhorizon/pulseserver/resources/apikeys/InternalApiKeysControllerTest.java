package org.dreamhorizon.pulseserver.resources.apikeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.constant.CronJobType;
import org.dreamhorizon.pulseserver.resources.internal.models.CronRedisSyncJobAcceptedRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;
import org.dreamhorizon.pulseserver.service.cron.CronRedisMaterializationJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalApiKeysControllerTest {

  @Mock
  private ProjectApiKeyService apiKeyService;
  @Mock
  private CronRedisMaterializationJobService cronRedisMaterializationJobService;

  private InternalApiKeysController controller;

  @BeforeEach
  void setUp() {
    controller = new InternalApiKeysController(apiKeyService, cronRedisMaterializationJobService);
  }

  @Test
  void shouldReturnAcceptedPayloadWhenEnqueueSucceeds() throws Exception {
    when(cronRedisMaterializationJobService.acceptApiKeysSyncToRedis()).thenReturn(
        Single.just(CronRedisSyncJobAcceptedRestResponse.builder()
            .jobId(42L)
            .deduplicated(false)
            .jobType(CronJobType.API_KEYS_TO_REDIS)
            .build()));

    Response<CronRedisSyncJobAcceptedRestResponse> response =
        controller.syncApiKeysToRedis().toCompletableFuture().get();

    assertThat(response.getHttpStatusCode()).isEqualTo(202);
    assertThat(response.getData().getJobId()).isEqualTo(42L);
    assertThat(response.getData().isDeduplicated()).isFalse();
    assertThat(response.getData().getJobType()).isEqualTo(CronJobType.API_KEYS_TO_REDIS);
  }
}
