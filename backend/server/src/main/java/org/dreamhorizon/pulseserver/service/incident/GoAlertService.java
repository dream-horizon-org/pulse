package org.dreamhorizon.pulseserver.service.incident;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;

/**
 * Fetches on-call users from GoAlert and resolves them to Slack mentions.
 *
 * <p>Flow: GoAlert GraphQL (service → onCallUsers → user emails)
 * → Slack API (users.lookupByEmail → user IDs) → {@code <@SLACKID>} format.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class GoAlertService {

  private static final String ON_CALL_FALLBACK = "N/A";
  private static final String SLACK_LOOKUP_URL =
      "https://slack.com/api/users.lookupByEmail?email=";

  private final WebClient webClient;
  private final NotificationConfig notificationConfig;

  /**
   * Fetches on-call user emails from GoAlert (stepNumber == 0),
   * resolves each to a Slack user ID, and returns them as
   * comma-separated {@code <@SLACKID>} mentions.
   *
   * @return e.g. {@code "<@U123>, <@U456>"} or "N/A" on any failure
   */
  public Single<String> getOnCallUserNames() {
    NotificationConfig.GoAlertConfig goAlertConfig =
        notificationConfig.getIncidentConfig().getGoAlert();

    if (goAlertConfig == null
        || goAlertConfig.getGoAlertUrl() == null
        || goAlertConfig.getGoAlertServiceId() == null) {
      log.warn("GoAlert config is missing or incomplete, returning fallback");
      return Single.just(ON_CALL_FALLBACK);
    }

    String url = goAlertConfig.getGoAlertUrl();
    String apiKey = goAlertConfig.getGoAlertApiKey();
    String serviceId = goAlertConfig.getGoAlertServiceId();
    String slackBotToken = goAlertConfig.getSlackBotToken();

    return fetchOnCallUsers(url, apiKey, serviceId)
        .flatMap(onCallUsers -> resolveUserEmails(url, apiKey, onCallUsers))
        .flatMap(emails -> formatAsSlackMentions(emails, slackBotToken))
        .onErrorReturnItem(ON_CALL_FALLBACK);
  }

  // ===================== GoAlert: fetch on-call users =====================

  private Single<JsonArray> fetchOnCallUsers(String url, String apiKey, String serviceId) {
    JsonObject payload = new JsonObject()
        .put("operationName", "service")
        .put("variables", new JsonObject().put("id", serviceId));

    return webClient.postAbs(url)
        .putHeader("Authorization", "Bearer " + apiKey)
        .putHeader("Content-Type", "application/json")
        .rxSendJsonObject(payload)
        .map(response -> {
          if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(
                "GoAlert service query failed: HTTP " + response.statusCode());
          }
          JsonObject json = response.bodyAsJsonObject();
          JsonObject service = json.getJsonObject("data", new JsonObject())
              .getJsonObject("service");
          if (service == null) {
            return new JsonArray();
          }
          return service.getJsonArray("onCallUsers", new JsonArray());
        })
        .doOnError(e -> log.error("Failed to fetch on-call users for serviceId={}", serviceId, e));
  }

  // ===================== GoAlert: resolve user emails =====================

  private Single<List<String>> resolveUserEmails(
      String url, String apiKey, JsonArray onCallUsers) {
    if (onCallUsers.isEmpty()) {
      return Single.just(List.of());
    }

    var stepZeroUsers = IntStream.range(0, onCallUsers.size())
        .mapToObj(onCallUsers::getJsonObject)
        .filter(u -> {
          Integer step = u.getInteger("stepNumber");
          return step != null && step == 0;
        })
        .toList();

    if (stepZeroUsers.isEmpty()) {
      return Single.just(List.of());
    }

    return Flowable.fromIterable(stepZeroUsers)
        .flatMapSingle(onCallUser -> {
          String userId = onCallUser.getString("userID");
          return fetchUserEmail(url, apiKey, userId)
              .onErrorReturnItem("");
        })
        .filter(email -> !email.isEmpty())
        .toList();
  }

  private Single<String> fetchUserEmail(String url, String apiKey, String userId) {
    JsonObject payload = new JsonObject()
        .put("operationName", "user")
        .put("variables", new JsonObject().put("id", userId));

    return webClient.postAbs(url)
        .putHeader("Authorization", "Bearer " + apiKey)
        .putHeader("Content-Type", "application/json")
        .rxSendJsonObject(payload)
        .map(response -> {
          if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(
                "GoAlert user query failed: HTTP " + response.statusCode());
          }
          JsonObject json = response.bodyAsJsonObject();
          JsonObject user = json.getJsonObject("data", new JsonObject())
              .getJsonObject("user");
          if (user == null) {
            return "";
          }
          return user.getString("email", "");
        });
  }

  // ===================== Slack: resolve emails → <@SLACKID> =====================

  private Single<String> formatAsSlackMentions(List<String> emails, String slackBotToken) {
    if (emails.isEmpty()) {
      return Single.just(ON_CALL_FALLBACK);
    }

    if (slackBotToken == null || slackBotToken.isBlank()) {
      log.warn("Slack bot token not configured, returning emails as fallback");
      return Single.just(String.join(", ", emails));
    }

    return Flowable.fromIterable(emails)
        .flatMapSingle(email -> lookupSlackUserId(email, slackBotToken)
            .onErrorReturnItem(email))
        .map(idOrEmail -> idOrEmail.startsWith("U") || idOrEmail.startsWith("W")
            ? String.format("<@%s>", idOrEmail)
            : idOrEmail)
        .collect(Collectors.joining(", "));
  }

  private Single<String> lookupSlackUserId(String email, String slackBotToken) {
    return webClient.getAbs(SLACK_LOOKUP_URL + email)
        .putHeader("Authorization", "Bearer " + slackBotToken)
        .rxSend()
        .map(response -> {
          JsonObject json = response.bodyAsJsonObject();
          if (json.getBoolean("ok", false)) {
            return json.getJsonObject("user").getString("id");
          }
          log.warn("Slack lookupByEmail failed for {}: {}", email, json.getString("error"));
          return email;
        });
  }
}
