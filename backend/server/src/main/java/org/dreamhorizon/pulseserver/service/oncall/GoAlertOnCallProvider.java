package org.dreamhorizon.pulseserver.service.oncall;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class GoAlertOnCallProvider implements OnCallProvider {

  private final WebClient webClient;
  private final NotificationConfig notificationConfig;

  @Override
  public Single<List<OnCallUser>> getOnCallUsers() {
    String noServiceId = null;
    return getOnCallUsers(noServiceId);
  }

  @Override
  public Single<List<OnCallUser>> getOnCallUsers(String serviceId) {
    NotificationConfig.GoAlertConfig goAlertConfig =
        notificationConfig.getIncidentConfig().getGoAlert();

    if (goAlertConfig == null || goAlertConfig.getGoAlertUrl() == null) {
      log.warn("GoAlert config is missing or incomplete");
      return Single.just(List.of());
    }

    String resolvedServiceId = (serviceId != null && !serviceId.isBlank())
        ? serviceId
        : goAlertConfig.getGoAlertServiceId();

    if (resolvedServiceId == null || resolvedServiceId.isBlank()) {
      log.warn("No GoAlert service ID resolved");
      return Single.just(List.of());
    }

    String url = goAlertConfig.getGoAlertUrl();
    String apiKey = goAlertConfig.getGoAlertApiKey();
    String userApiKey = goAlertConfig.getGoAlertUserApiKey();

    return fetchOnCallUserIds(url, apiKey, resolvedServiceId)
        .flatMap(onCallUsers -> resolveUsers(url, userApiKey != null ? userApiKey : apiKey, onCallUsers));
  }

  private String buildAuthUrl(String url, String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      return url;
    }
    String encoded = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    return url + (url.contains("?") ? "&" : "?") + "token=" + encoded;
  }

  private Single<JsonArray> fetchOnCallUserIds(String url, String apiKey, String serviceId) {
    JsonObject payload = new JsonObject()
        .put("query",
            "query service($id: ID!) { service(id: $id) { onCallUsers { userID userName stepNumber } } }")
        .put("operationName", "service")
        .put("variables", new JsonObject().put("id", serviceId));

    String authUrl = buildAuthUrl(url, apiKey);
    log.info("GoAlert request url={} serviceId={}", url, serviceId);

    return webClient.postAbs(authUrl)
        .putHeader("Content-Type", "application/json")
        .rxSendJsonObject(payload)
        .map(response -> {
          if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = response.bodyAsString();
            log.error("GoAlert service query failed: HTTP {} body={}", response.statusCode(), body);
            throw new RuntimeException(
                "GoAlert service query failed: HTTP " + response.statusCode() + " body=" + body);
          }
          JsonObject json = response.bodyAsJsonObject();
          log.info("GoAlert raw response: {}", json.encode());
          if (json.containsKey("errors")) {
            log.warn("GoAlert returned GraphQL errors: {}", json.getJsonArray("errors"));
          }
          JsonObject data = json.getJsonObject("data");
          if (data == null) {
            return new JsonArray();
          }
          JsonObject service = data.getJsonObject("service");
          if (service == null) {
            return new JsonArray();
          }
          return service.getJsonArray("onCallUsers", new JsonArray());
        })
        .doOnError(e -> log.error("Failed to fetch on-call users for serviceId={}", serviceId, e));
  }

  private Single<List<OnCallUser>> resolveUsers(String url, String apiKey, JsonArray onCallUsers) {
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

    log.info("GoAlert step-0 users: {}", stepZeroUsers);

    if (stepZeroUsers.isEmpty()) {
      return Single.just(List.of());
    }

    return Flowable.fromIterable(stepZeroUsers)
        .flatMapSingle(user -> {
          String userId = user.getString("userID");
          String name = user.getString("userName", "");
          return fetchUserEmail(url, apiKey, userId)
              .map(email -> {
                log.info("GoAlert resolved user: userId={} name={} email={}", userId, name, email);
                return new OnCallUser(name, email);
              })
              .onErrorReturnItem(new OnCallUser(name, ""));
        })
        .filter(u -> !u.getEmail().isEmpty())
        .toList();
  }

  private Single<String> fetchUserEmail(String url, String apiKey, String userId) {
    JsonObject payload = new JsonObject()
        .put("query", "query user($id: ID!) { user(id: $id) { id name email } }")
        .put("operationName", "user")
        .put("variables", new JsonObject().put("id", userId));

    String authUrl = buildAuthUrl(url, apiKey);

    return webClient.postAbs(authUrl)
        .putHeader("Content-Type", "application/json")
        .rxSendJsonObject(payload)
        .map(response -> {
          if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = response.bodyAsString();
            log.error("GoAlert user query failed: HTTP {} body={}", response.statusCode(), body);
            throw new RuntimeException(
                "GoAlert user query failed: HTTP " + response.statusCode());
          }
          JsonObject json = response.bodyAsJsonObject();
          log.info("GoAlert user response for userId={}: {}", userId, json.encode());
          JsonObject data = json.getJsonObject("data");
          if (data == null) {
            return "";
          }
          JsonObject user = data.getJsonObject("user");
          if (user == null) {
            return "";
          }
          return user.getString("email", "");
        });
  }
}
