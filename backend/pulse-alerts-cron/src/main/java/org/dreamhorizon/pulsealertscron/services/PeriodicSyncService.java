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
  private final UsageLimitNotificationService notificationService;
  
  private Long usageLimitsTimerId;
  private Long apiKeysTimerId;
  private Long notificationTimerId;
  
  private static final long USAGE_LIMITS_INTERVAL_SECONDS = 5; // 5 seconds
  private static final long API_KEYS_INTERVAL_SECONDS = 10 * 60; // 10 minutes
  private static final long NOTIFICATION_INTERVAL_SECONDS = 60 * 60; // 1 hour

  @Inject
  public PeriodicSyncService(Vertx vertx, WebClient webClient, ApplicationConfig config) {
    this.vertx = vertx;
    
    ClickhouseService clickhouseService = new ClickhouseService(config);
    PulseServerApiClient apiClient = new PulseServerApiClient(webClient, config);
    this.redisService = new RedisService(vertx, config);
    this.dataSyncService = new DataSyncService(clickhouseService, apiClient, redisService);
    this.notificationService = new UsageLimitNotificationService(apiClient);
  }

  /**
   * Starts all periodic sync tasks
   */
  public void start() {
    log.info("🚀 Starting Periodic Sync Service");
    
    // Start usage limits sync (5 seconds)
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
    
    // Start usage notification processing (1 hour)
    log.info("📧 Starting Usage Notification processing (interval: {} seconds)", NOTIFICATION_INTERVAL_SECONDS);
    executeNotificationProcessing();
    this.notificationTimerId = vertx.setPeriodic(NOTIFICATION_INTERVAL_SECONDS * 1000, id -> {
      executeNotificationProcessing();
    });
    log.info("✅ Usage Notification processing started with timer ID: {}", notificationTimerId);
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
    
    if (notificationTimerId != null) {
      vertx.cancelTimer(notificationTimerId);
      log.info("✅ Cancelled notification timer: {}", notificationTimerId);
      notificationTimerId = null;
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

  private void executeNotificationProcessing() {
    notificationService.processNotifications()
        .subscribe(
            () -> log.info("✅ Usage notification processing completed successfully"),
            error -> log.error("❌ Usage notification processing failed", error)
        );
  }
}

