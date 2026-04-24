package org.dreamhorizon.pulseserver.service.kong;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitInfo;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KongUsageCreditsRedisSyncServiceTest {

  private static final String MAX_USER_SESSIONS = "max_user_sessions_per_project";
  private static final String MAX_EVENTS = "max_events_per_project";

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

  private static void injectRedis(
      KongUsageCreditsRedisSyncService service, Redis redis, RedisAPI api) throws Exception {
    Field clientField = KongUsageCreditsRedisSyncService.class.getDeclaredField("redisClient");
    clientField.setAccessible(true);
    clientField.set(service, redis);
    Field apiField = KongUsageCreditsRedisSyncService.class.getDeclaredField("redisApi");
    apiField.setAccessible(true);
    apiField.set(service, api);
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

  @Test
  void shouldReturnZeroWhenNoActiveLimits() throws Exception {
    when(applicationConfig.getRedisHost()).thenReturn("localhost");
    when(applicationConfig.getRedisPort()).thenReturn(6379);
    when(clickhouseQueryService.getCurrentMonthUsage()).thenReturn(Single.just(Map.of()));
    when(usageLimitService.getAllActiveLimits()).thenReturn(Flowable.empty());

    KongUsageCreditsRedisSyncService service = new KongUsageCreditsRedisSyncService(
        clickhouseQueryService, usageLimitService, vertx, applicationConfig);
    injectRedis(service, mock(Redis.class), mock(RedisAPI.class));

    service.syncUsageCreditsToRedis().test().assertComplete().assertValue(0);
  }

  @Test
  void shouldWriteCreditsForProjectsWithActiveLimits() throws Exception {
    when(applicationConfig.getRedisHost()).thenReturn("localhost");
    when(applicationConfig.getRedisPort()).thenReturn(6379);

    UsageLimitValue sessionLimit =
        UsageLimitValue.builder().finalThreshold(100L).build();
    UsageLimitValue eventLimit = UsageLimitValue.builder().finalThreshold(200L).build();
    Map<String, UsageLimitValue> limits = new HashMap<>();
    limits.put(MAX_USER_SESSIONS, sessionLimit);
    limits.put(MAX_EVENTS, eventLimit);

    ProjectUsageLimitInfo p1 =
        ProjectUsageLimitInfo.builder().projectId("proj-1").usageLimits(limits).build();
    ProjectUsageLimitInfo p2 =
        ProjectUsageLimitInfo.builder()
            .projectId("proj-2")
            .usageLimits(
                Map.of(
                    MAX_USER_SESSIONS,
                    UsageLimitValue.builder().finalThreshold(10L).build(),
                    MAX_EVENTS,
                    UsageLimitValue.builder().finalThreshold(20L).build()))
            .build();

    when(usageLimitService.getAllActiveLimits()).thenReturn(Flowable.fromArray(p1, p2));
    when(clickhouseQueryService.getCurrentMonthUsage())
        .thenReturn(
            Single.just(
                Map.of(
                    "proj-1",
                    UsageStats.builder()
                        .projectId("proj-1")
                        .sessionsUsed(5L)
                        .eventsUsed(7L)
                        .build(),
                    "proj-2",
                    UsageStats.builder()
                        .projectId("proj-2")
                        .sessionsUsed(null)
                        .eventsUsed(null)
                        .build())));

    RedisAPI redisApi = mock(RedisAPI.class);
    when(redisApi.hset(anyList())).thenReturn(Future.succeededFuture());

    KongUsageCreditsRedisSyncService service = new KongUsageCreditsRedisSyncService(
        clickhouseQueryService, usageLimitService, vertx, applicationConfig);
    injectRedis(service, mock(Redis.class), redisApi);

    service.syncUsageCreditsToRedis().test().assertComplete().assertValue(2);

    verify(redisApi, times(2)).hset(anyList());
  }

  @Test
  void shouldUseZeroUsageWhenClickHouseHasNoRowForProject() throws Exception {
    when(applicationConfig.getRedisHost()).thenReturn("localhost");
    when(applicationConfig.getRedisPort()).thenReturn(6379);

    Map<String, UsageLimitValue> limits =
        Map.of(
            MAX_USER_SESSIONS,
            UsageLimitValue.builder().finalThreshold(50L).build(),
            MAX_EVENTS,
            UsageLimitValue.builder().finalThreshold(80L).build());
    ProjectUsageLimitInfo p =
        ProjectUsageLimitInfo.builder().projectId("orphan").usageLimits(limits).build();

    when(usageLimitService.getAllActiveLimits()).thenReturn(Flowable.just(p));
    when(clickhouseQueryService.getCurrentMonthUsage()).thenReturn(Single.just(Map.of()));

    RedisAPI redisApi = mock(RedisAPI.class);
    when(redisApi.hset(anyList())).thenReturn(Future.succeededFuture());

    KongUsageCreditsRedisSyncService service = new KongUsageCreditsRedisSyncService(
        clickhouseQueryService, usageLimitService, vertx, applicationConfig);
    injectRedis(service, mock(Redis.class), redisApi);

    service.syncUsageCreditsToRedis().test().assertComplete().assertValue(1);
    verify(redisApi).hset(anyList());
  }

  @Test
  void shouldTreatNullThresholdValuesAsZero() throws Exception {
    when(applicationConfig.getRedisHost()).thenReturn("localhost");
    when(applicationConfig.getRedisPort()).thenReturn(6379);

    Map<String, UsageLimitValue> limits = new HashMap<>();
    limits.put(MAX_USER_SESSIONS, UsageLimitValue.builder().finalThreshold(null).build());
    limits.put(MAX_EVENTS, null);
    ProjectUsageLimitInfo p =
        ProjectUsageLimitInfo.builder().projectId("proj-x").usageLimits(limits).build();

    when(usageLimitService.getAllActiveLimits()).thenReturn(Flowable.just(p));
    when(clickhouseQueryService.getCurrentMonthUsage()).thenReturn(Single.just(Map.of()));

    RedisAPI redisApi = mock(RedisAPI.class);
    when(redisApi.hset(anyList())).thenReturn(Future.succeededFuture());

    KongUsageCreditsRedisSyncService service = new KongUsageCreditsRedisSyncService(
        clickhouseQueryService, usageLimitService, vertx, applicationConfig);
    injectRedis(service, mock(Redis.class), redisApi);

    service.syncUsageCreditsToRedis().test().assertComplete().assertValue(1);
    verify(redisApi).hset(anyList());
  }

  @Test
  void shouldHandleNullUsageLimitsMap() throws Exception {
    when(applicationConfig.getRedisHost()).thenReturn("localhost");
    when(applicationConfig.getRedisPort()).thenReturn(6379);

    ProjectUsageLimitInfo p =
        ProjectUsageLimitInfo.builder().projectId("proj-n").usageLimits(null).build();

    when(usageLimitService.getAllActiveLimits()).thenReturn(Flowable.just(p));
    when(clickhouseQueryService.getCurrentMonthUsage()).thenReturn(Single.just(Map.of()));

    RedisAPI redisApi = mock(RedisAPI.class);
    when(redisApi.hset(anyList())).thenReturn(Future.succeededFuture());

    KongUsageCreditsRedisSyncService service = new KongUsageCreditsRedisSyncService(
        clickhouseQueryService, usageLimitService, vertx, applicationConfig);
    injectRedis(service, mock(Redis.class), redisApi);

    service.syncUsageCreditsToRedis().test().assertComplete().assertValue(1);
    verify(redisApi).hset(anyList());
  }

  @Test
  void shouldFailWhenRedisWriteFails() throws Exception {
    when(applicationConfig.getRedisHost()).thenReturn("localhost");
    when(applicationConfig.getRedisPort()).thenReturn(6379);

    Map<String, UsageLimitValue> limits =
        Map.of(
            MAX_USER_SESSIONS,
            UsageLimitValue.builder().finalThreshold(1L).build(),
            MAX_EVENTS,
            UsageLimitValue.builder().finalThreshold(1L).build());
    ProjectUsageLimitInfo p =
        ProjectUsageLimitInfo.builder().projectId("proj-fail").usageLimits(limits).build();

    when(usageLimitService.getAllActiveLimits()).thenReturn(Flowable.just(p));
    when(clickhouseQueryService.getCurrentMonthUsage()).thenReturn(Single.just(Map.of()));

    RedisAPI redisApi = mock(RedisAPI.class);
    when(redisApi.hset(anyList()))
        .thenReturn(Future.failedFuture(new RuntimeException("hset failed")));

    KongUsageCreditsRedisSyncService service = new KongUsageCreditsRedisSyncService(
        clickhouseQueryService, usageLimitService, vertx, applicationConfig);
    injectRedis(service, mock(Redis.class), redisApi);

    service.syncUsageCreditsToRedis().test().assertFailure(RuntimeException.class);
  }
}
