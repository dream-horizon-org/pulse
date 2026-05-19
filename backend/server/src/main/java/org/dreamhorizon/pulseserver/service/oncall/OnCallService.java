package org.dreamhorizon.pulseserver.service.oncall;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class OnCallService {

  private static final String ON_CALL_FALLBACK = "N/A";
  private static final String SLACK_LOOKUP_URL =
      "https://slack.com/api/users.lookupByEmail?email=";

  private final OnCallProvider onCallProvider;
  private final WebClient webClient;
  private final NotificationConfig notificationConfig;

  public Single<List<OnCallUser>> getOnCallUsers() {
    return onCallProvider.getOnCallUsers()
        .onErrorReturnItem(List.of());
  }

  public Single<String> getOnCallSlackMentions() {
    return getOnCallSlackMentions(null);
  }

  public Single<String> getOnCallSlackMentions(String goalertServiceId) {
    String slackBotToken = resolveSlackBotToken();

    return onCallProvider.getOnCallUsers(goalertServiceId)
        .flatMap(users -> formatAsSlackMentions(users, slackBotToken))
        .onErrorReturnItem(ON_CALL_FALLBACK);
  }

  private String resolveSlackBotToken() {
    NotificationConfig.GoAlertConfig goAlertConfig =
        notificationConfig.getIncidentConfig().getGoAlert();
    return goAlertConfig != null ? goAlertConfig.getSlackBotToken() : null;
  }

  private Single<String> formatAsSlackMentions(List<OnCallUser> users, String slackBotToken) {
    if (users.isEmpty()) {
      return Single.just(ON_CALL_FALLBACK);
    }

    List<String> emails = users.stream()
        .map(OnCallUser::getEmail)
        .filter(e -> e != null && !e.isEmpty())
        .toList();

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

  public Single<String> lookupSlackUserId(String email, String slackBotToken) {
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
