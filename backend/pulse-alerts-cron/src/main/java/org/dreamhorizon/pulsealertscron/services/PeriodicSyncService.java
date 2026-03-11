package org.dreamhorizon.pulsealertscron.services;

import com.google.inject.Inject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.client.PulseServerApiClient;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;

@Slf4j
public class PeriodicSyncService {
  private final Vertx vertx;
  private final DataSyncService dataSyncService;
  private final RedisService redisService;
  
  private Long usageLimitsTimerId;
  private Long apiKeysTimerId;
  
  private static final long USAGE_LIMITS_INTERVAL_SECONDS = 5; // 5 minutes
  private static final long API_KEYS_INTERVAL_SECONDS = 10 * 60; // 10 minutes

  @Inject
  public PeriodicSyncService(Vertx vertx, WebClient webClient, ApplicationConfig config) {
    this.vertx = vertx;
    
    ClickhouseService clickhouseService = new ClickhouseService(config);
    PulseServerApiClient apiClient = new PulseServerApiClient(webClient, config);
    this.redisService = new RedisService(vertx, config);
    this.dataSyncService = new DataSyncService(clickhouseService, apiClient, redisService);
  }

  /**
   * Starts all periodic sync tasks
   */
  public void start() {
    log.info("🚀 Starting Periodic Sync Service");
    
    // Start usage limits sync (5 minutes)
    log.info("📊 Starting Usage Limits sync (interval: {} seconds)", USAGE_LIMITS_INTERVAL_SECONDS);
    executeUsageLimitsSync();
    this.usageLimitsTimerId = vertx.setPeriodic(USAGE_LIMITS_INTERVAL_SECONDS * 1000, id -> {
      executeUsageLimitsSync();
    });
    log.info("✅ Usage Limits sync started with timer ID: {}", usageLimitsTimerId);
    
    // Start API keys sync (10 minutes)
    log.info("🔑 Starting API Keys sync (interval: {} seconds)", API_KEYS_INTERVAL_SECONDS);
    executeApiKeysSync();
    this.apiKeysTimerId = vertx.setPeriodic(API_KEYS_INTERVAL_SECONDS * 1000, id -> {
      executeApiKeysSync();
    });
    log.info("✅ API Keys sync started with timer ID: {}", apiKeysTimerId);
  }

  /**
   * Stops all periodic sync tasks and cleans up resources
   */
  public void stop() {
    log.info("🛑 Stopping Periodic Sync Service");
    
    if (usageLimitsTimerId != null) {
      vertx.cancelTimer(usageLimitsTimerId);
      log.info("✅ Cancelled usage limits timer: {}", usageLimitsTimerId);
      usageLimitsTimerId = null;
    }
    
    if (apiKeysTimerId != null) {
      vertx.cancelTimer(apiKeysTimerId);
      log.info("✅ Cancelled API keys timer: {}", apiKeysTimerId);
      apiKeysTimerId = null;
    }
    
    // Close Redis connection
    redisService.close();
    
    log.info("✅ Periodic Sync Service stopped successfully");
  }

  private void executeUsageLimitsSync() {
    dataSyncService.processUsageLimits()
        .subscribe(
            () -> log.info("✅ Usage limits sync completed successfully"),
            error -> log.error("❌ Usage limits sync failed", error)
        );
  }
  
  private void executeApiKeysSync() {
    dataSyncService.syncApiKeys()
        .subscribe(
            () -> log.info("✅ API keys sync completed successfully"),
            error -> log.error("❌ API keys sync failed", error)
        );
  }
}
