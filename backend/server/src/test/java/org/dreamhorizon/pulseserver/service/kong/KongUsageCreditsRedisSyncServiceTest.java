package org.dreamhorizon.pulseserver.service.kong;

import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KongUsageCreditsRedisSyncServiceTest {

  @Mock
  private ClickhouseQueryService clickhouseQueryService;
  @Mock
  private UsageLimitService usageLimitService;
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

    KongUsageCreditsRedisSyncService service = new KongUsageCreditsRedisSyncService(
        clickhouseQueryService, usageLimitService, vertx, applicationConfig);

    service.syncUsageCreditsToRedis().test().assertFailure(IllegalStateException.class);
  }

  @Test
  void shouldFailFastWhenRedisHostBlank() {
    when(applicationConfig.getRedisHost()).thenReturn("  ");
    when(applicationConfig.getRedisPort()).thenReturn(6379);

    KongUsageCreditsRedisSyncService service = new KongUsageCreditsRedisSyncService(
        clickhouseQueryService, usageLimitService, vertx, applicationConfig);

    service.syncUsageCreditsToRedis().test().assertFailure(IllegalStateException.class);
  }

  @Test
  void shouldFailFastWhenRedisPortMissing() {
    when(applicationConfig.getRedisHost()).thenReturn("localhost");
    when(applicationConfig.getRedisPort()).thenReturn(null);

    KongUsageCreditsRedisSyncService service = new KongUsageCreditsRedisSyncService(
        clickhouseQueryService, usageLimitService, vertx, applicationConfig);

    service.syncUsageCreditsToRedis().test().assertFailure(IllegalStateException.class);
  }
}
