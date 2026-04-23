package org.dreamhorizon.pulsealertscron.services;

import com.google.inject.Inject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.client.PulseServerApiClient;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.constant.Constants;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class PeriodicSyncService {
  private final Vertx vertx;
  private final DataSyncService dataSyncService;
  private final UsageLimitNotificationService notificationService;

  private Long usageLimitsTimerId;
  private Long apiKeysTimerId;
  private Long notificationTimerId;

  private final ApplicationConfig applicationConfig;

  /** Ensures at most one {@link DataSyncService#processUsageLimits()} runs at a time (avoids overlapping POSTs). */
  private final AtomicBoolean usageLimitsSyncInFlight = new AtomicBoolean(false);

  /** Ensures at most one {@link DataSyncService#syncApiKeys()} runs at a time (avoids overlapping POSTs). */
  private final AtomicBoolean apiKeysSyncInFlight = new AtomicBoolean(false);

  private static final long NOTIFICATION_INTERVAL_SECONDS =  24 * 60 * 60; // 24 hours

  @Inject
  public PeriodicSyncService(Vertx vertx, WebClient webClient, ApplicationConfig config) {
    this.vertx = vertx;
    this.applicationConfig = config;

    PulseServerApiClient apiClient = new PulseServerApiClient(webClient, config);
    this.dataSyncService = new DataSyncService(apiClient);
    this.notificationService = new UsageLimitNotificationService(apiClient);
  }

  /**
   * Starts all periodic sync tasks
   */
  public void start() {
    log.info("🚀 Starting Periodic Sync Service");

    long usageIntervalSec = applicationConfig.resolveUsageCreditsSyncIntervalSeconds();
    long apiKeysIntervalSec = applicationConfig.resolveApiKeysSyncIntervalSeconds();

    // Usage credits → Redis (pulse-server async 202 + dedupe); interval from app config
    log.info("Starting usage credits Redis enqueue sync (interval: {} seconds)", usageIntervalSec);
    executeUsageLimitsSync();
    this.usageLimitsTimerId = vertx.setPeriodic(usageIntervalSec * 1000, id -> {
      executeUsageLimitsSync();
    });
    log.info("Usage credits sync timer started, id={}", usageLimitsTimerId);

    log.info("Starting API keys Redis enqueue sync (interval: {} seconds)", apiKeysIntervalSec);
    executeApiKeysSync();
    this.apiKeysTimerId = vertx.setPeriodic(apiKeysIntervalSec * 1000, id -> {
      executeApiKeysSync();
    });
    log.info("API keys sync timer started, id={}", apiKeysTimerId);

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

    log.info("✅ Periodic Sync Service stopped successfully");
  }

  private void executeUsageLimitsSync() {
    if (!usageLimitsSyncInFlight.compareAndSet(false, true)) {
      log.info("{} Skipping usage limits sync: previous run still in progress",
          Constants.USAGE_LIMITS_SYNC_LOG_PREFIX);
      return;
    }
    dataSyncService.processUsageLimits()
        .doFinally(() -> usageLimitsSyncInFlight.set(false))
        .subscribe(
            () -> log.info("{} Usage credits sync cycle completed (HTTP layer)",
                Constants.USAGE_LIMITS_SYNC_LOG_PREFIX),
            error -> log.error("{} Usage credits sync cycle failed",
                Constants.USAGE_LIMITS_SYNC_LOG_PREFIX, error)
        );
  }

  private void executeApiKeysSync() {
    if (!apiKeysSyncInFlight.compareAndSet(false, true)) {
      log.info("{} Skipping API keys sync: previous run still in progress",
          Constants.API_KEYS_SYNC_LOG_PREFIX);
      return;
    }
    dataSyncService.syncApiKeys()
        .doFinally(() -> apiKeysSyncInFlight.set(false))
        .subscribe(
            () -> log.info("{} API keys sync completed successfully",
                Constants.API_KEYS_SYNC_LOG_PREFIX),
            error -> log.error("{} API keys sync failed",
                Constants.API_KEYS_SYNC_LOG_PREFIX, error)
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
