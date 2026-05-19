package org.dreamhorizon.pulseserver.service.oncall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.List;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.config.NotificationConfig.GoAlertConfig;
import org.dreamhorizon.pulseserver.config.NotificationConfig.IncidentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnCallServiceTest {

  @Mock OnCallProvider onCallProvider;
  @Mock WebClient webClient;
  @Mock NotificationConfig notificationConfig;
  @Mock HttpRequest<Buffer> httpRequest;
  @Mock HttpResponse<Buffer> httpResponse;

  OnCallService service;

  @BeforeEach
  void setUp() {
    IncidentConfig incidentConfig = new IncidentConfig();
    GoAlertConfig goAlertConfig = new GoAlertConfig();
    goAlertConfig.setSlackBotToken("xoxb-test-token");
    incidentConfig.setGoAlert(goAlertConfig);
    when(notificationConfig.getIncidentConfig()).thenReturn(incidentConfig);

    service = new OnCallService(onCallProvider, webClient, notificationConfig);
  }

  @Nested
  class GetOnCallUsers {

    @Test
    void shouldReturnUsersFromProvider() {
      List<OnCallUser> users = List.of(new OnCallUser("Alice", "alice@test.com"));
      when(onCallProvider.getOnCallUsers()).thenReturn(Single.just(users));

      List<OnCallUser> result = service.getOnCallUsers().blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getName()).isEqualTo("Alice");
    }

    @Test
    void shouldReturnEmptyListOnProviderError() {
      when(onCallProvider.getOnCallUsers())
          .thenReturn(Single.error(new RuntimeException("provider down")));

      List<OnCallUser> result = service.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GetOnCallSlackMentions {

    @Test
    void shouldReturnFallbackWhenNoUsers() {
      when(onCallProvider.getOnCallUsers(any())).thenReturn(Single.just(List.of()));

      String result = service.getOnCallSlackMentions().blockingGet();

      assertThat(result).isEqualTo("N/A");
    }

    @Test
    void shouldReturnFallbackOnProviderError() {
      when(onCallProvider.getOnCallUsers(any()))
          .thenReturn(Single.error(new RuntimeException("timeout")));

      String result = service.getOnCallSlackMentions().blockingGet();

      assertThat(result).isEqualTo("N/A");
    }

    @Test
    void shouldReturnFallbackWhenUsersHaveNoEmail() {
      List<OnCallUser> users = List.of(new OnCallUser("Alice", ""));
      when(onCallProvider.getOnCallUsers(any())).thenReturn(Single.just(users));

      String result = service.getOnCallSlackMentions().blockingGet();

      assertThat(result).isEqualTo("N/A");
    }

    @Test
    void shouldReturnEmailsWhenSlackTokenNotConfigured() {
      IncidentConfig incidentConfig = new IncidentConfig();
      GoAlertConfig goAlertConfig = new GoAlertConfig();
      incidentConfig.setGoAlert(goAlertConfig);
      when(notificationConfig.getIncidentConfig()).thenReturn(incidentConfig);
      service = new OnCallService(onCallProvider, webClient, notificationConfig);

      List<OnCallUser> users = List.of(new OnCallUser("Alice", "alice@test.com"));
      when(onCallProvider.getOnCallUsers(any())).thenReturn(Single.just(users));

      String result = service.getOnCallSlackMentions().blockingGet();

      assertThat(result).isEqualTo("alice@test.com");
    }

    @Test
    void shouldReturnEmailsWhenGoAlertConfigIsNull() {
      IncidentConfig incidentConfig = new IncidentConfig();
      when(notificationConfig.getIncidentConfig()).thenReturn(incidentConfig);
      service = new OnCallService(onCallProvider, webClient, notificationConfig);

      List<OnCallUser> users = List.of(new OnCallUser("Alice", "alice@test.com"));
      when(onCallProvider.getOnCallUsers(any())).thenReturn(Single.just(users));

      String result = service.getOnCallSlackMentions().blockingGet();

      assertThat(result).isEqualTo("alice@test.com");
    }

    @Test
    void shouldFormatSlackMentionWhenLookupSucceeds() {
      List<OnCallUser> users = List.of(new OnCallUser("Alice", "alice@test.com"));
      when(onCallProvider.getOnCallUsers(any())).thenReturn(Single.just(users));

      JsonObject slackResponse = new JsonObject()
          .put("ok", true)
          .put("user", new JsonObject().put("id", "U12345"));

      when(webClient.getAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsJsonObject()).thenReturn(slackResponse);

      String result = service.getOnCallSlackMentions().blockingGet();

      assertThat(result).isEqualTo("<@U12345>");
    }

    @Test
    void shouldFallbackToEmailWhenSlackLookupFails() {
      List<OnCallUser> users = List.of(new OnCallUser("Alice", "alice@test.com"));
      when(onCallProvider.getOnCallUsers(any())).thenReturn(Single.just(users));

      JsonObject slackResponse = new JsonObject()
          .put("ok", false)
          .put("error", "users_not_found");

      when(webClient.getAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsJsonObject()).thenReturn(slackResponse);

      String result = service.getOnCallSlackMentions().blockingGet();

      assertThat(result).isEqualTo("alice@test.com");
    }

    @Test
    void shouldHandleMultipleUsersWithMixedSlackResults() {
      List<OnCallUser> users = List.of(
          new OnCallUser("Alice", "alice@test.com"),
          new OnCallUser("Bob", "bob@test.com"));
      when(onCallProvider.getOnCallUsers(any())).thenReturn(Single.just(users));

      JsonObject aliceSlack = new JsonObject()
          .put("ok", true)
          .put("user", new JsonObject().put("id", "U11111"));
      JsonObject bobSlack = new JsonObject()
          .put("ok", false)
          .put("error", "users_not_found");

      when(webClient.getAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsJsonObject())
          .thenReturn(aliceSlack)
          .thenReturn(bobSlack);

      String result = service.getOnCallSlackMentions().blockingGet();

      assertThat(result).contains("<@U11111>");
      assertThat(result).contains("bob@test.com");
    }

    @Test
    void shouldFallbackToEmailWhenSlackApiThrowsError() {
      List<OnCallUser> users = List.of(new OnCallUser("Alice", "alice@test.com"));
      when(onCallProvider.getOnCallUsers(any())).thenReturn(Single.just(users));

      when(webClient.getAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSend())
          .thenReturn(Single.error(new RuntimeException("connection refused")));

      String result = service.getOnCallSlackMentions().blockingGet();

      assertThat(result).isEqualTo("alice@test.com");
    }
  }
}
