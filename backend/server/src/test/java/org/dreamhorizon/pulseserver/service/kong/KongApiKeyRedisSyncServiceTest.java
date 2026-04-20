package org.dreamhorizon.pulseserver.service.kong;

import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KongApiKeyRedisSyncServiceTest {

  @Mock
  private ProjectApiKeyService projectApiKeyService;
  @Mock
  private ApplicationConfig applicationConfig;

  private final Vertx vertx = Vertx.vertx();

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  @Test
  void shouldFailFastWhenRedisHostMissing() {
    when(applicationConfig.getRedisHost()).thenReturn(null);
    when(applicationConfig.getRedisPort()).thenReturn(6379);

    KongApiKeyRedisSyncService service =
        new KongApiKeyRedisSyncService(projectApiKeyService, vertx, applicationConfig);

    service.syncValidApiKeysToRedis().test().assertFailure(IllegalStateException.class);
  }

  @Test
  void shouldFailFastWhenRedisHostBlank() {
    when(applicationConfig.getRedisHost()).thenReturn("  ");
    when(applicationConfig.getRedisPort()).thenReturn(6379);

    KongApiKeyRedisSyncService service =
        new KongApiKeyRedisSyncService(projectApiKeyService, vertx, applicationConfig);

    service.syncValidApiKeysToRedis().test().assertFailure(IllegalStateException.class);
  }

  @Test
  void shouldFailFastWhenRedisPortMissing() {
    when(applicationConfig.getRedisHost()).thenReturn("localhost");
    when(applicationConfig.getRedisPort()).thenReturn(null);

    KongApiKeyRedisSyncService service =
        new KongApiKeyRedisSyncService(projectApiKeyService, vertx, applicationConfig);

    service.syncValidApiKeysToRedis().test().assertFailure(IllegalStateException.class);
  }
}
