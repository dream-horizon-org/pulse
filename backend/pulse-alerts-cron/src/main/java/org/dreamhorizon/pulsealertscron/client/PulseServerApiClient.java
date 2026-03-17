package org.dreamhorizon.pulsealertscron.client;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.dto.response.ApiKeysResponse;
import org.dreamhorizon.pulsealertscron.dto.response.UsageLimitsApiResponse;
import org.dreamhorizon.pulsealertscron.dto.response.UsageNotificationDto;
import org.dreamhorizon.pulsealertscron.dto.response.UsageNotificationResponse;

import java.util.List;

@Slf4j
public class PulseServerApiClient {
  private final WebClient webClient;
  private final String apiBaseUrl;
  private final String serviceJwt;
  
  private static final String ACTIVE_LIMITS_PATH = "/internal/v1/projects/limits";
  private static final String VALID_API_KEYS_PATH = "/internal/v1/api-keys/valid";
  private static final String USAGE_NOTIFICATIONS_PATH = "/internal/v1/projects/limits/notifications-due";
  private static final String MARK_NOTIFICATIONS_PATH = "/internal/v1/projects/%s/limits/notifications";
  private static final String SEND_NOTIFICATION_PATH = "/v1/notifications/send";
  private static final long REQUEST_TIMEOUT_MS = 30000;

  @Inject
  public PulseServerApiClient(WebClient webClient, ApplicationConfig config) {
    this.webClient = webClient;
    this.apiBaseUrl = config.getPulseServerUrl();
    this.serviceJwt = config.getServiceJwtSecret();
  }

  public Single<UsageLimitsApiResponse.Response> getActiveLimits() {
    log.info("Fetching active usage limits from API: {}", apiBaseUrl + ACTIVE_LIMITS_PATH);
    
    return Single.defer(() -> 
        webClient
            .getAbs(apiBaseUrl + ACTIVE_LIMITS_PATH)
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
                log.error("❌ Failed to fetch usage limits: {}", errorMsg);
                throw new RuntimeException(errorMsg);
              }
              
              var wrappedResponse = response.bodyAsJsonObject();
              
              var dataObject = wrappedResponse.getJsonObject("data");
              UsageLimitsApiResponse.Response result = dataObject.mapTo(UsageLimitsApiResponse.Response.class);
              
              log.info("✅ Successfully fetched {} active usage limits", result.getTotalCount());
              return result;
            })
            .doOnError(error -> 
                log.error("❌ Error calling usage limits API", error)
            )
    );
  }

  public Single<ApiKeysResponse.Response> getValidApiKeys() {
    log.info("Fetching valid API keys from: {}", apiBaseUrl + VALID_API_KEYS_PATH);
    
    return Single.defer(() -> 
        webClient
            .getAbs(apiBaseUrl + VALID_API_KEYS_PATH)
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
                log.error("❌ Failed to fetch API keys: {}", errorMsg);
                throw new RuntimeException(errorMsg);
              }
              
              var wrappedResponse = response.bodyAsJsonObject();
              var dataObject = wrappedResponse.getJsonObject("data");
              ApiKeysResponse.Response result = dataObject.mapTo(ApiKeysResponse.Response.class);
              
              log.info("✅ Successfully fetched {} valid API keys", result.getCount());
              return result;
            })
            .doOnError(error -> 
                log.error("❌ Error calling API keys API", error)
            )
    );
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
                log.error("❌ Failed to fetch usage notifications: {}", errorMsg);
                throw new RuntimeException(errorMsg);
              }
              
              var wrappedResponse = response.bodyAsJsonObject();
              var dataObject = wrappedResponse.getJsonObject("data");
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
            .map(response -> {
              int statusCode = response.statusCode();
              
              if (statusCode != 200) {
                String errorMsg = String.format(
                    "Failed to mark notifications: status %d: %s",
                    statusCode,
                    response.bodyAsString()
                );
                log.error("❌ {}", errorMsg);
                throw new RuntimeException(errorMsg);
              }
              
              log.info("✅ Marked thresholds {} as notified for project {}", thresholds, projectId);
              return response;
            })
    ).ignoreElement();
  }

  /**
   * Send usage limit notification via pulse-server notification API.
   * Template selection and display logic is handled server-side.
   */
  public Completable sendUsageLimitNotification(UsageNotificationDto notification) {
    log.info("Sending usage limit notification for project: {} - {}% threshold ({}) using template {}",
        notification.getProjectId(), notification.getThreshold(), notification.getNotifyFor(), notification.getTemplateName());
    
    // Calculate overage limit for metadata
    Integer eventsOverage = notification.getEventsOverage() != null ? notification.getEventsOverage() : 0;
    Integer sessionsOverage = notification.getSessionsOverage() != null ? notification.getSessionsOverage() : 0;
    int maxOverage = Math.max(eventsOverage, sessionsOverage);
    int overageLimit = 100 + maxOverage;
    
    // Build metadata with all details from server (percentages already capped for display)
    JsonObject metadata = new JsonObject()
        .put("projectId", notification.getProjectId())
        .put("notifyFor", notification.getNotifyFor())
        .put("threshold", notification.getThreshold())
        .put("sessionsUsed", notification.getSessionsUsed())
        .put("sessionsLimit", notification.getSessionsLimit())
        .put("sessionsPercentage", notification.getSessionsPercentage())
        .put("sessionsOverage", sessionsOverage)
        .put("sessionsBlocked", notification.getSessionsBlocked() != null ? notification.getSessionsBlocked() : false)
        .put("sessionsAtLimit", notification.getSessionsAtLimit() != null ? notification.getSessionsAtLimit() : false)
        .put("eventsUsed", notification.getEventsUsed())
        .put("eventsLimit", notification.getEventsLimit())
        .put("eventsPercentage", notification.getEventsPercentage())
        .put("eventsOverage", eventsOverage)
        .put("eventsBlocked", notification.getEventsBlocked() != null ? notification.getEventsBlocked() : false)
        .put("eventsAtLimit", notification.getEventsAtLimit() != null ? notification.getEventsAtLimit() : false)
        .put("overageLimit", overageLimit)
        .put("hasOverage", maxOverage > 0);
    
    JsonObject body = new JsonObject()
        .put("eventName", notification.getTemplateName())
        .put("channelTypes", new JsonArray().add("EMAIL"))
        .put("metadata", metadata);
    
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
                log.error("❌ Failed to send notification: status {}: {}",
                    statusCode, response.bodyAsString());
                throw new RuntimeException("Failed to send notification");
              }
              
              log.info("✅ Notification sent successfully for project {} - {}% ({}) using template {}",
                  notification.getProjectId(), notification.getThreshold(), notification.getNotifyFor(), notification.getTemplateName());
              return response;
            })
    ).ignoreElement();
  }
}

