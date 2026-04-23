package org.dreamhorizon.pulsealertscron.client;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.constant.Constants;
import org.dreamhorizon.pulsealertscron.dto.response.CronRedisSyncJobAcceptedDto;
import org.dreamhorizon.pulsealertscron.dto.response.UsageNotificationDto;
import org.dreamhorizon.pulsealertscron.dto.response.UsageNotificationResponse;

import java.util.List;

@Slf4j
public class PulseServerApiClient {
  private final WebClient webClient;
  private final String apiBaseUrl;
  private final String serviceJwt;

  private static final String SYNC_USAGE_CREDITS_TO_REDIS_PATH = "/internal/v1/projects/limits/sync-to-redis";
  private static final String SYNC_API_KEYS_TO_REDIS_PATH = "/internal/v1/api-keys/sync-to-redis";
  private static final String PROCESS_USAGE_LIMIT_NOTIFICATIONS_PATH =
      "/internal/v1/projects/limits/process-usage-notifications";
  private static final String USAGE_NOTIFICATIONS_PATH = "/internal/v1/projects/limits/notifications-due";
  private static final String MARK_NOTIFICATIONS_PATH = "/internal/v1/projects/%s/limits/notifications";
  private static final String SEND_NOTIFICATION_PATH = "/v1/notifications/send";
  private static final long REQUEST_TIMEOUT_MS = 30000;
  private static final String DEFAULT_DASHBOARD_URL = "https://pulse-ux.com";

  @Inject
  public PulseServerApiClient(WebClient webClient, ApplicationConfig config) {
    this.webClient = webClient;
    this.apiBaseUrl = config.getPulseServerUrl();
    this.serviceJwt = config.getServiceJwtSecret();
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

  /**
   * Get usage notifications that need to be sent.
   * Calls the analysis endpoint that checks all projects.
   */
  public Single<UsageNotificationResponse> getUsageNotifications() {
    log.info("Fetching usage notifications from: {}", apiBaseUrl + USAGE_NOTIFICATIONS_PATH);
    
    return Single.defer(() ->
        webClient
            .getAbs(apiBaseUrl + USAGE_NOTIFICATIONS_PATH)
            .putHeader("Authorization", "Bearer " + serviceJwt)
            .timeout(REQUEST_TIMEOUT_MS)
            .rxSend()
            .map(response -> {
              int statusCode = response.statusCode();
              
              if (statusCode != 200) {
                String errorMsg = String.format(
                    "API returned status %d: %s",
                    statusCode,
                    response.bodyAsString()
                );
                log.error("❌ Failed to fetch usage notifications: API returned status {}: {}", statusCode, response.bodyAsString());
                throw new RuntimeException(errorMsg);
              }
              
              JsonObject wrappedResponse = response.bodyAsJsonObject();
              JsonObject dataObject = wrappedResponse.getJsonObject("data");
              UsageNotificationResponse result = dataObject.mapTo(UsageNotificationResponse.class);
              
              log.info("✅ Successfully fetched {} usage notifications", result.getNotificationsDue());
              return result;
            })
            .doOnError(error ->
                log.error("❌ Error calling usage notifications API", error)
            )
    );
  }

  /**
   * Mark thresholds as notified for a project.
   */
  public Completable markThresholdsNotified(String projectId, List<Integer> thresholds) {
    String url = apiBaseUrl + String.format(MARK_NOTIFICATIONS_PATH, projectId);
    log.info("Marking thresholds as notified for project {}: {}", projectId, thresholds);
    
    JsonObject body = new JsonObject()
        .put("thresholds", new JsonArray(thresholds));
    
    return Single.defer(() ->
        webClient
            .postAbs(url)
            .putHeader("Authorization", "Bearer " + serviceJwt)
            .putHeader("Content-Type", "application/json")
            .timeout(REQUEST_TIMEOUT_MS)
            .rxSendJsonObject(body)
            .doOnSuccess(response -> log.info(
                "Mark thresholds HTTP response received: project={} status={} (timeout={}ms)",
                projectId, response.statusCode(), REQUEST_TIMEOUT_MS))
            .map(response -> {
              int statusCode = response.statusCode();
              
              if (statusCode != 200) {
                String responseBody = response.bodyAsString();
                String errorMsg = String.format(
                    "Failed to mark notifications: status %d: %s",
                    statusCode,
                    responseBody
                );
                log.error(
                    "❌ Failed to mark notifications: status {} body={}",
                    statusCode,
                    responseBody);
                throw new RuntimeException(errorMsg);
              }
              log.info("✅ Marked thresholds {} as notified for project {}", thresholds, projectId);
              return response;
            })
    ).doOnError(error -> log.error(
        "Mark thresholds request failed: project={} url={}",
        projectId,
        url,
        error))
        .ignoreElement();
  }



  /**
   * Send usage limit notification via pulse-server notification API.
   * Template selection and display logic is handled server-side.
   * Sends params for template placeholder replacement (projectName, threshold, etc.).
   */
  public Completable sendUsageLimitNotification(UsageNotificationDto notification) {
    log.info("Sending usage limit notification for project: {} - {}% threshold ({}) using template {}",
        notification.getProjectId(), notification.getThreshold(), notification.getNotifyFor(), notification.getTemplateName());

    // Build params for template placeholders ({{projectName}}, {{threshold}}, etc.)
    // Templates use eventsPercentageDisplay/sessionsPercentageDisplay; fallback to numeric when null
    String eventsDisplay = notification.getEventsPercentageDisplay() != null
        ? notification.getEventsPercentageDisplay()
        : String.valueOf(notification.getEventsPercentage() != null ? notification.getEventsPercentage() : 0);
    String sessionsDisplay = notification.getSessionsPercentageDisplay() != null
        ? notification.getSessionsPercentageDisplay()
        : String.valueOf(notification.getSessionsPercentage() != null ? notification.getSessionsPercentage() : 0);
    JsonObject params = new JsonObject()
        .put("projectId", notification.getProjectId())
        .put("projectName", notification.getProjectName() != null ? notification.getProjectName() : notification.getProjectId())
        .put("threshold", notification.getThreshold())
        .put("notifyFor", notification.getNotifyFor())
        .put("eventsPercentageDisplay", eventsDisplay)
        .put("sessionsPercentageDisplay", sessionsDisplay)
        .put("dashboardUrl", DEFAULT_DASHBOARD_URL);
    if (notification.getTenantId() != null && !notification.getTenantId().isBlank()) {
      params.put("tenantId", notification.getTenantId());
    }

    JsonObject body = new JsonObject()
        .put("eventName", notification.getTemplateName())
        .put("channelTypes", new JsonArray().add("EMAIL"))
        .put("params", params);

      List<String> recipientEmails = notification.getRecipientEmails();
      if (recipientEmails != null && !recipientEmails.isEmpty()) {
        JsonArray emailsArray = new JsonArray();
        recipientEmails.stream()
            .filter(e -> e != null && !e.isBlank() && e.contains("@"))
            .forEach(emailsArray::add);
        if (!emailsArray.isEmpty()) {
          body.put("recipients", new JsonObject().put("emails", emailsArray));
        }
      }
    
    return Single.defer(() ->
        webClient
            .postAbs(apiBaseUrl + SEND_NOTIFICATION_PATH)
            .putHeader("Authorization", "Bearer " + serviceJwt)
            .putHeader("Content-Type", "application/json")
            .putHeader("X-Project-Id", notification.getProjectId())
            .timeout(REQUEST_TIMEOUT_MS)
            .rxSendJsonObject(body)
            .map(response -> {
              int statusCode = response.statusCode();
              
              if (statusCode != 200 && statusCode != 201) {
                String responseBody = response.bodyAsString();
                log.error("❌ Failed to send notification: status {} body={}", statusCode, responseBody);
                throw new RuntimeException("Failed to send notification");
              }

              log.info("✅ Notification sent successfully for project {} - {}% ({}) using template {}",
                  notification.getProjectId(), notification.getThreshold(), notification.getNotifyFor(), notification.getTemplateName());
              return response;
            })
    ).doOnError(error -> log.error(
        "Send usage-limit notification request failed: project={} url={}",
        notification.getProjectId(),
        apiBaseUrl + SEND_NOTIFICATION_PATH,
        error))
        .ignoreElement();
  }
}

