package org.dreamhorizon.pulsealertscron.client;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.constant.Constants;
import org.dreamhorizon.pulsealertscron.dto.response.CronRedisSyncJobAcceptedDto;

@Slf4j
public class PulseServerApiClient {
  private final WebClient webClient;
  private final String apiBaseUrl;
  private final String serviceJwt;
  private final ApplicationConfig config;

  private static final String SYNC_USAGE_CREDITS_TO_REDIS_PATH = "/internal/v1/projects/limits/sync-to-redis";
  private static final String SYNC_API_KEYS_TO_REDIS_PATH = "/internal/v1/api-keys/sync-to-redis";
  private static final String PROCESS_USAGE_LIMIT_NOTIFICATIONS_PATH =
      "/internal/v1/projects/limits/process-usage-notifications";
  private static final long REQUEST_TIMEOUT_MS = 30000;

  @Inject
  public PulseServerApiClient(WebClient webClient, ApplicationConfig config) {
    this.webClient = webClient;
    this.apiBaseUrl = config.getPulseServerUrl();
    this.serviceJwt = config.getServiceJwtSecret();
    this.config = config;
  }

  /** Daily funnel batch — {@code InternalAnalyticsController} POST funnels. */
  public Completable triggerFunnelBatch() {
    String endpoint = apiBaseUrl + config.getBatchFunnelsEndpoint();
    return triggerBatchJob("FUNNELS_DAILY", endpoint);
  }

  public Completable triggerJourneyBatch() {
    String endpoint = apiBaseUrl + config.getBatchJourneysEndpoint();
    return triggerBatchJob("JOURNEYS_DAILY", endpoint);
  }

  public Completable triggerEventsBatch() {
    String endpoint = apiBaseUrl + config.getBatchEventsEndpoint();
    return triggerBatchJob("EVENTS_INCREMENTAL", endpoint);
  }

  private Completable triggerBatchJob(String jobType, String endpoint) {
    log.info("[triggerBatchJob] Triggering {} batch job at: {}", jobType, endpoint);
    
    return Single.defer(() ->
        webClient
            .postAbs(endpoint)
            .putHeader("Authorization", "Bearer " + serviceJwt)
            .putHeader("Content-Type", "application/json")
            .timeout(REQUEST_TIMEOUT_MS)
            .rxSend()
            .map(response -> {
                int statusCode = response.statusCode();
                if (statusCode < 200 || statusCode >= 300) {
                    String errorMsg = String.format(
                        "Batch job %s failed with status %d: %s",
                        jobType, statusCode, response.bodyAsString()
                    );
                    log.error("[triggerBatchJob] {}", errorMsg);
                    throw new RuntimeException(errorMsg);
                }
                log.info("[triggerBatchJob] Successfully triggered {} batch job", jobType);
                return response;
            })
            .doOnError(error ->
                log.error("[triggerBatchJob] Error triggering {} batch job", jobType, error)
            )
    ).ignoreElement();
  }

  /**
   * Delegates usage credits (ClickHouse + limits → Redis) to pulse-server (Part B). Same auth as other internal routes.
   */
  public Completable syncUsageCreditsToRedis() {
    String url = apiBaseUrl + SYNC_USAGE_CREDITS_TO_REDIS_PATH;
    log.info("Calling pulse-server usage credits Redis sync: {}", url);

    return webClient
        .postAbs(url)
        .putHeader("Authorization", "Bearer " + serviceJwt)
        .timeout(REQUEST_TIMEOUT_MS)
        .rxSend()
        .flatMapCompletable(response -> {
          int statusCode = response.statusCode();
          String body = response.bodyAsString();
          if (statusCode != 200 && statusCode != 202) {
            String errorMsg = String.format(
                "API returned status %d: %s",
                statusCode,
                body);
            log.error("{} Failed usage credits Redis sync: {}", Constants.USAGE_LIMITS_SYNC_LOG_PREFIX, errorMsg);
            return Completable.error(new RuntimeException(errorMsg));
          }
          CronRedisSyncJobAcceptedDto.tryParse(body).ifPresentOrElse(
              ack -> log.info(
                  "{} Usage credits → Redis sync HTTP {} — jobId={} deduplicated={} jobType={}",
                  Constants.USAGE_LIMITS_SYNC_LOG_PREFIX,
                  statusCode,
                  ack.getJobId(),
                  ack.isDeduplicated(),
                  ack.getJobType()),
              () -> log.info(
                  "{} Usage credits → Redis sync HTTP {} (no job envelope in body; legacy or empty)",
                  Constants.USAGE_LIMITS_SYNC_LOG_PREFIX,
                  statusCode));
          return Completable.complete();
        })
        .doOnError(error -> log.error("Error calling usage credits Redis sync", error));
  }

  /**
   * Delegates API key → Redis sync to pulse-server (Part A migration). Same auth as other internal routes.
   */
  public Completable syncApiKeysToRedis() {
    String url = apiBaseUrl + SYNC_API_KEYS_TO_REDIS_PATH;
    log.info("Calling pulse-server API key Redis sync: {}", url);

    return webClient
        .postAbs(url)
        .putHeader("Authorization", "Bearer " + serviceJwt)
        .timeout(REQUEST_TIMEOUT_MS)
        .rxSend()
        .flatMapCompletable(response -> {
          int statusCode = response.statusCode();
          if (statusCode != 200 && statusCode != 202) {
            String errorMsg = String.format(
                "API returned status %d: %s",
                statusCode,
                response.bodyAsString());
            log.error("Failed API key Redis sync: {}", errorMsg);
            return Completable.error(new RuntimeException(errorMsg));
          }
          log.info("API key Redis sync accepted or completed via pulse-server (status {})",
              statusCode);
          return Completable.complete();
        })
        .doOnError(error -> log.error("Error calling API key Redis sync", error));
  }

  /**
   * Enqueues usage-limit notification processing on pulse-server (async 202 + cron_jobs_history).
   */
  public Completable processUsageLimitNotifications() {
    String url = apiBaseUrl + PROCESS_USAGE_LIMIT_NOTIFICATIONS_PATH;
    log.info("Calling pulse-server usage-limit notifications batch: {}", url);

    return webClient
        .postAbs(url)
        .putHeader("Authorization", "Bearer " + serviceJwt)
        .timeout(REQUEST_TIMEOUT_MS)
        .rxSend()
        .flatMapCompletable(
            response -> {
              int statusCode = response.statusCode();
              String body = response.bodyAsString();
              if (statusCode != 200 && statusCode != 202) {
                String errorMsg =
                    String.format("API returned status %d: %s", statusCode, body);
                log.error(
                    "{} Failed usage-limit notifications enqueue: {}",
                    Constants.USAGE_LIMIT_NOTIFICATIONS_SYNC_LOG_PREFIX,
                    errorMsg);
                return Completable.error(new RuntimeException(errorMsg));
              }
              CronRedisSyncJobAcceptedDto.tryParse(body)
                  .ifPresentOrElse(
                      ack ->
                          log.info(
                              "{} Usage-limit notifications HTTP {} — jobId={} deduplicated={} jobType={}",
                              Constants.USAGE_LIMIT_NOTIFICATIONS_SYNC_LOG_PREFIX,
                              statusCode,
                              ack.getJobId(),
                              ack.isDeduplicated(),
                              ack.getJobType()),
                      () ->
                          log.info(
                              "{} Usage-limit notifications HTTP {} (no job envelope in body)",
                              Constants.USAGE_LIMIT_NOTIFICATIONS_SYNC_LOG_PREFIX,
                              statusCode));
              return Completable.complete();
            })
        .doOnError(
            error ->
                log.error(
                    "{} Error calling usage-limit notifications enqueue",
                    Constants.USAGE_LIMIT_NOTIFICATIONS_SYNC_LOG_PREFIX,
                    error));
  }
}
