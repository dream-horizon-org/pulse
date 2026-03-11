package org.dreamhorizon.pulsealertscron.services;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.Future;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import io.vertx.rxjava3.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.dto.response.ApiKeysResponse;
import org.dreamhorizon.pulsealertscron.dto.response.ProjectUsageResult;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RedisService {
  private static final String API_KEY_MAP = "{pulse}:apikey_map";
  
  private final Redis redisClient;
  private final RedisAPI redisAPI;

  @Inject
  public RedisService(Vertx vertx, ApplicationConfig config) {
    String redisHost = config.getRedisHost();
    int redisPort = config.getRedisPort();
    
    log.info("Initializing RedisService - host: {}, port: {}", redisHost, redisPort);
    
    RedisOptions options = new RedisOptions()
        .setConnectionString("redis://" + redisHost + ":" + redisPort)
        .setMaxPoolSize(32)
        .setMaxPoolWaiting(128);
    
    this.redisClient = Redis.createClient(vertx.getDelegate(), options);
    this.redisAPI = RedisAPI.api(redisClient);
    
    log.info("✅ RedisService initialized successfully");
  }

  /**
   * Saves project credits (remaining) for all projects to Redis
   * Key pattern: project:{projectId}:credit
   * 
   * For each project, stores a hash with:
   * - remaining_session_credit
   * - remaining_event_credit
   */
  public Completable saveUsageLimits(List<ProjectUsageResult> results) {
    log.info("📦 Saving credits for {} projects", results.size());
    
    List<Future<Void>> futures = new ArrayList<>();
    
    for (ProjectUsageResult result : results) {
      String key = "project:" + result.getProjectId() + ":credit";
      
      log.debug("Setting project credits: {} (session: {}, event: {})", 
          result.getProjectId(), result.getSessionsRemaining(), result.getEventsRemaining());
      
      List<String> args = List.of(
          key,
          "remaining_session_credit", String.valueOf(result.getSessionsRemaining()),
          "remaining_event_credit", String.valueOf(result.getEventsRemaining())
      );
      
      Future<Void> future = redisAPI.hset(args)
          .onSuccess(v -> log.debug("✅ Set credits for project: {}", result.getProjectId()))
          .onFailure(err -> log.error("❌ Failed to set credits for project: {}", result.getProjectId(), err))
          .mapEmpty();
      
      futures.add(future);
    }
    
    return Completable.create(emitter -> {
      Future.all(futures)
          .onSuccess(v -> {
            log.info("✅ Saved credits for {} projects", results.size());
            emitter.onComplete();
          })
          .onFailure(err -> {
            log.error("❌ Failed to save credits for some projects", err);
            emitter.onError(err);
          });
    });
  }

  /**
   * Replaces all API key mappings in Redis atomically using MULTI/EXEC
   * 
   * Key: {pulse}:apikey_map (Redis Hash)
   * Field: <api_key_value>
   * Value: <project_id>
   */
  public Completable saveApiKeyMappings(List<ApiKeysResponse.ApiKey> apiKeys) {
    log.info("🔄 Replacing API key mappings in Redis ({} keys) - ATOMIC", apiKeys.size());
    
    // Prepare HSET args outside transaction
    List<String> hsetArgs = new ArrayList<>();
    hsetArgs.add(API_KEY_MAP);
    for (ApiKeysResponse.ApiKey apiKey : apiKeys) {
      hsetArgs.add(apiKey.getApiKey());
      hsetArgs.add(apiKey.getProjectId());
    }
    
    // Execute transaction - EXEC is always called
    return Completable.create(emitter -> {
      redisAPI.multi()
          .compose(v -> redisAPI.del(List.of(API_KEY_MAP)))
          .compose(v -> {
            if (!apiKeys.isEmpty()) {
              return redisAPI.hset(hsetArgs);
            }
            return Future.succeededFuture();
          })
          .compose(v -> redisAPI.exec())
          .onSuccess(v -> {
            log.info("✅ Atomically replaced {} API key mappings", apiKeys.size());
            emitter.onComplete();
          })
          .onFailure(err -> {
            log.error("❌ Failed to replace API key mappings atomically", err);
            emitter.onError(err);
          });
    });
  }

  /**
   * Closes Redis connection and cleans up resources
   */
  public void close() {
    log.info("Closing Redis connection");
    if (redisClient != null) {
      redisClient.close();
    }
  }
}
