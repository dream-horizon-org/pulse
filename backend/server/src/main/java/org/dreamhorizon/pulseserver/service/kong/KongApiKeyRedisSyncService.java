package org.dreamhorizon.pulseserver.service.kong;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyInfo;

/** Writes valid API keys to the Redis hash consumed by Kong (see {@link Constants#KONG_API_KEY_MAP_REDIS_KEY}). */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class KongApiKeyRedisSyncService {

  private final ProjectApiKeyService projectApiKeyService;
  private final Vertx vertx;
  private final ApplicationConfig applicationConfig;

  private volatile Redis redisClient;
  private volatile RedisAPI redisApi;

  /**
   * Loads all valid API keys from MySQL and atomically replaces the Kong API key hash in Redis.
   */
  public Single<Integer> syncValidApiKeysToRedis() {
    if (!isRedisConfigured()) {
      return Single.error(new IllegalStateException(
          "Redis is not configured (redisHost / redisPort). Cannot sync API keys to Redis."));
    }
    ensureRedisClient();

    return projectApiKeyService.getAllValidApiKeys()
        .toList()
        .flatMap(keys -> replaceApiKeyMapAtomically(keys).toSingleDefault(keys.size()));
  }

  private boolean isRedisConfigured() {
    String host = applicationConfig.getRedisHost();
    Integer port = applicationConfig.getRedisPort();
    return host != null && !host.trim().isEmpty() && port != null;
  }

  private synchronized void ensureRedisClient() {
    if (redisClient != null) {
      return;
    }
    String host = applicationConfig.getRedisHost().trim();
    int port = applicationConfig.getRedisPort();
    RedisOptions options = new RedisOptions()
        .setConnectionString("redis://" + host + ":" + port)
        .setMaxPoolSize(32)
        .setMaxPoolWaiting(128);
    redisClient = Redis.createClient(vertx, options);
    redisApi = RedisAPI.api(redisClient);
    log.info("KongApiKeyRedisSyncService: Redis client initialized for {}:{}", host, port);
  }

  /**
   * Same semantics as pulse-alerts-cron {@code RedisService.saveApiKeyMappings}: MULTI, DEL hash key,
   * HSET all fields, EXEC.
   */
  private Completable replaceApiKeyMapAtomically(List<ApiKeyInfo> apiKeys) {
    String mapKey = Constants.KONG_API_KEY_MAP_REDIS_KEY;
    RedisAPI api = this.redisApi;

    List<String> hsetArgs = new ArrayList<>();
    hsetArgs.add(mapKey);
    for (ApiKeyInfo key : apiKeys) {
      hsetArgs.add(key.getRawApiKey());
      hsetArgs.add(key.getProjectId());
    }

    return Completable.create(emitter -> api.multi()
        .compose(v -> api.del(List.of(mapKey)))
        .compose(v -> {
          if (!apiKeys.isEmpty()) {
            return api.hset(hsetArgs);
          }
          return Future.succeededFuture();
        })
        .compose(v -> api.exec())
        .onSuccess(v -> {
          log.info("Atomically replaced {} API key mappings in Redis key {}", apiKeys.size(), mapKey);
          emitter.onComplete();
        })
        .onFailure(err -> {
          log.error("Failed to replace API key mappings in Redis", err);
          emitter.onError(err);
        }));
  }
}
