package org.dreamhorizon.pulsealertscron.services;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.client.PulseServerApiClient;
import org.dreamhorizon.pulsealertscron.constant.Constants;

@Slf4j
public class DataSyncService {
  private final PulseServerApiClient apiClient;

  @Inject
  public DataSyncService(PulseServerApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public Completable processUsageLimits() {
    log.info("{} Starting usage limits processing", Constants.USAGE_LIMITS_SYNC_LOG_PREFIX);
    long startTime = System.currentTimeMillis();

    return apiClient.syncUsageCreditsToRedis()
        .doOnComplete(() -> {
          long duration = System.currentTimeMillis() - startTime;
          log.info(
              "{} Usage credits sync request finished in {}ms (pulse-server enqueues async work; "
                  + "see pulse-server logs / cron_jobs_history for completion)",
              Constants.USAGE_LIMITS_SYNC_LOG_PREFIX,
              duration);
        })
        .doOnError(error -> {
          long duration = System.currentTimeMillis() - startTime;
          log.error("{} Usage processing failed after {}ms",
              Constants.USAGE_LIMITS_SYNC_LOG_PREFIX, duration, error);
        });
  }

  public Completable syncApiKeys() {
    log.info("=== Starting API Keys Sync (via pulse-server) ===");
    long startTime = System.currentTimeMillis();

    return apiClient.syncApiKeysToRedis()
        .doOnComplete(() -> {
          long duration = System.currentTimeMillis() - startTime;
          log.info("API keys sync completed in {}ms (pulse-server wrote Redis)", duration);
        })
        .doOnError(error -> {
          long duration = System.currentTimeMillis() - startTime;
          log.error("API keys sync failed after {}ms", duration, error);
        });
  }
}
