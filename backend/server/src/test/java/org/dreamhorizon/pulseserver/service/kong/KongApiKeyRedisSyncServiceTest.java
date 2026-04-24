package org.dreamhorizon.pulseserver.service.kong;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Flowable;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import java.lang.reflect.Field;
import java.util.List;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyInfo;
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

  private static void injectRedis(KongApiKeyRedisSyncService service, Redis redis, RedisAPI api)
      throws Exception {
    Field clientField = KongApiKeyRedisSyncService.class.getDeclaredField("redisClient");
    clientField.setAccessible(true);
    clientField.set(service, redis);
    Field apiField = KongApiKeyRedisSyncService.class.getDeclaredField("redisApi");
    apiField.setAccessible(true);
    apiField.set(service, api);
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

  @Test
  void shouldReplaceApiKeyMapAtomicallyWhenEmpty() throws Exception {
    when(applicationConfig.getRedisHost()).thenReturn("localhost");
    when(applicationConfig.getRedisPort()).thenReturn(6379);
    when(projectApiKeyService.getAllValidApiKeys()).thenReturn(Flowable.empty());

    RedisAPI redisApi = mock(RedisAPI.class);
    when(redisApi.multi()).thenReturn(Future.succeededFuture());
    when(redisApi.del(anyList())).thenReturn(Future.succeededFuture());
    when(redisApi.exec()).thenReturn(Future.succeededFuture());

    KongApiKeyRedisSyncService service =
        new KongApiKeyRedisSyncService(projectApiKeyService, vertx, applicationConfig);
    injectRedis(service, mock(Redis.class), redisApi);

    service.syncValidApiKeysToRedis().test().assertComplete().assertValue(0);

    verify(redisApi).multi();
    verify(redisApi).del(List.of(Constants.KONG_API_KEY_MAP_REDIS_KEY));
    verify(redisApi, never()).hset(anyList());
    verify(redisApi).exec();
  }

  @Test
  void shouldReplaceApiKeyMapAtomicallyWhenKeysPresent() throws Exception {
    when(applicationConfig.getRedisHost()).thenReturn("localhost");
    when(applicationConfig.getRedisPort()).thenReturn(6379);
    ApiKeyInfo k1 =
        ApiKeyInfo.builder().rawApiKey("key-one").projectId("proj-a").build();
    ApiKeyInfo k2 =
        ApiKeyInfo.builder().rawApiKey("key-two").projectId("proj-b").build();
    when(projectApiKeyService.getAllValidApiKeys()).thenReturn(Flowable.fromArray(k1, k2));

    RedisAPI redisApi = mock(RedisAPI.class);
    when(redisApi.multi()).thenReturn(Future.succeededFuture());
    when(redisApi.del(anyList())).thenReturn(Future.succeededFuture());
    when(redisApi.hset(anyList())).thenReturn(Future.succeededFuture());
    when(redisApi.exec()).thenReturn(Future.succeededFuture());

    KongApiKeyRedisSyncService service =
        new KongApiKeyRedisSyncService(projectApiKeyService, vertx, applicationConfig);
    injectRedis(service, mock(Redis.class), redisApi);

    service.syncValidApiKeysToRedis().test().assertComplete().assertValue(2);

    verify(redisApi).hset(anyList());
    verify(redisApi).exec();
  }

  @Test
  void shouldFailWhenRedisPipelineFails() throws Exception {
    when(applicationConfig.getRedisHost()).thenReturn("localhost");
    when(applicationConfig.getRedisPort()).thenReturn(6379);
    when(projectApiKeyService.getAllValidApiKeys()).thenReturn(Flowable.empty());

    RedisAPI redisApi = mock(RedisAPI.class);
    when(redisApi.multi()).thenReturn(Future.succeededFuture());
    when(redisApi.del(anyList())).thenReturn(Future.failedFuture(new RuntimeException("redis down")));

    KongApiKeyRedisSyncService service =
        new KongApiKeyRedisSyncService(projectApiKeyService, vertx, applicationConfig);
    injectRedis(service, mock(Redis.class), redisApi);

    service.syncValidApiKeysToRedis().test().assertFailure(RuntimeException.class);
  }
}
