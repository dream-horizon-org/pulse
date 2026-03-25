package org.dreamhorizon.pulsealertscron.services;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
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
  
  // Batch job state management keys
  private static final String BATCH_LAST_EXECUTION_KEY = "{pulse}:batch:last_execution_date";
  private static final String BATCH_JOB_IN_PROGRESS_KEY = "{pulse}:batch:job_in_progress";
  private static final String BATCH_HISTORY_KEY_PREFIX = "{pulse}:batch:history:";
  private static final int JOB_IN_PROGRESS_TTL_SECONDS = 3600; // 1 hour safety TTL
  
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
          "session_credit", String.valueOf(result.getSessionsRemaining()),
          "event_credit", String.valueOf(result.getEventsRemaining())
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
   * Get the last batch job execution date from Redis
   * @return Single that emits the date string or null if not found
   */
  public Single<String> getLastBatchExecutionDate() {
    return Single.create(emitter -> {
      redisAPI.get(BATCH_LAST_EXECUTION_KEY)
          .onSuccess(response -> {
            String dateString = response != null ? response.toString() : null;
            log.debug("[getLastBatchExecutionDate] Retrieved: {}", dateString);
            emitter.onSuccess(dateString);
          })
          .onFailure(err -> {
            log.error("[getLastBatchExecutionDate] Error retrieving last execution date", err);
            emitter.onError(err);
          });
    });
  }

  /**
   * Set the last batch job execution date in Redis
   */
  public Completable setLastBatchExecutionDate(String date) {
    return Completable.create(emitter -> {
      redisAPI.set(List.of(BATCH_LAST_EXECUTION_KEY, date))
          .onSuccess(v -> {
            log.info("[setLastBatchExecutionDate] Set last execution date: {}", date);
            emitter.onComplete();
          })
          .onFailure(err -> {
            log.error("[setLastBatchExecutionDate] Error setting last execution date: {}", date, err);
            emitter.onError(err);
          });
    });
  }

  /**
   * Check if batch job is currently in progress
   */
  public Single<Boolean> isBatchJobInProgress() {
    return Single.create(emitter -> {
      redisAPI.get(BATCH_JOB_IN_PROGRESS_KEY)
          .onSuccess(response -> {
            boolean inProgress = response != null && "true".equals(response.toString());
            log.debug("[isBatchJobInProgress] Job in progress: {}", inProgress);
            emitter.onSuccess(inProgress);
          })
          .onFailure(err -> {
            log.error("[isBatchJobInProgress] Error checking job progress", err);
            emitter.onError(err);
          });
    });
  }

  /**
   * Set batch job in progress flag with TTL
   */
  public Completable setBatchJobInProgress(boolean inProgress) {
    return Completable.create(emitter -> {
      if (inProgress) {
        // Set with TTL
        redisAPI.setex(BATCH_JOB_IN_PROGRESS_KEY, String.valueOf(JOB_IN_PROGRESS_TTL_SECONDS), "true")
            .onSuccess(v -> {
              log.info("[setBatchJobInProgress] Set job in progress with TTL: {} seconds", JOB_IN_PROGRESS_TTL_SECONDS);
              emitter.onComplete();
            })
            .onFailure(err -> {
              log.error("[setBatchJobInProgress] Error setting job in progress", err);
              emitter.onError(err);
            });
      } else {
        // Delete the key
        redisAPI.del(List.of(BATCH_JOB_IN_PROGRESS_KEY))
            .onSuccess(v -> {
              log.info("[setBatchJobInProgress] Cleared job in progress flag");
              emitter.onComplete();
            })
            .onFailure(err -> {
              log.error("[setBatchJobInProgress] Error clearing job in progress", err);
              emitter.onError(err);
            });
      }
    });
  }

  /**
   * Save batch job execution history
   */
  public Completable saveBatchJobHistory(String date, String startedAt, String status, long durationMs) {
    String historyKey = BATCH_HISTORY_KEY_PREFIX + date;
    
    List<String> args = List.of(
        historyKey,
        "started_at", startedAt,
        "status", status,
        "duration_ms", String.valueOf(durationMs)
    );
    
    return Completable.create(emitter -> {
      redisAPI.hset(args)
          .onSuccess(v -> {
            log.info("[saveBatchJobHistory] Saved execution history for date: {} (status: {}, duration: {}ms)", 
                     date, status, durationMs);
            emitter.onComplete();
          })
          .onFailure(err -> {
            log.error("[saveBatchJobHistory] Error saving execution history for date: {}", date, err);
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
