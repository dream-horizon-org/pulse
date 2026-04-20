package org.dreamhorizon.pulseserver.resources.apikeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.resources.apikeys.models.ApiKeyRedisSyncRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;
import org.dreamhorizon.pulseserver.service.kong.KongApiKeyRedisSyncService;
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
  private KongApiKeyRedisSyncService kongApiKeyRedisSyncService;

  private InternalApiKeysController controller;

  @BeforeEach
  void setUp() {
    controller = new InternalApiKeysController(apiKeyService, kongApiKeyRedisSyncService);
  }

  @Test
  void shouldReturnSyncSummaryWhenKongSyncSucceeds() throws Exception {
    when(kongApiKeyRedisSyncService.syncValidApiKeysToRedis()).thenReturn(Single.just(5));

    Response<ApiKeyRedisSyncRestResponse> response =
        controller.syncApiKeysToRedis().toCompletableFuture().get();

    assertThat(response.getData().getKeysSynced()).isEqualTo(5);
    assertThat(response.getData().getRedisKey()).isEqualTo(Constants.KONG_API_KEY_MAP_REDIS_KEY);
    assertThat(response.getData().getDurationMs()).isGreaterThanOrEqualTo(0L);
  }
}
