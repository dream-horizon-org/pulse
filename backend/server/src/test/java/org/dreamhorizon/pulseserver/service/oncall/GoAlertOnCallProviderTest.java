package org.dreamhorizon.pulseserver.service.oncall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
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
class GoAlertOnCallProviderTest {

  private static final String URL = "http://goalert:8081/api/graphql";
  private static final String API_KEY = "test-service-api-key";
  private static final String USER_API_KEY = "test-user-api-key";
  private static final String SERVICE_ID = "svc-123";
  private static final String USER_ID = "user-abc";
  private static final String USER_NAME = "TestUser";
  private static final String USER_EMAIL = "test@example.com";

  @Mock WebClient webClient;
  @Mock HttpRequest<Buffer> httpRequest;
  @Mock HttpResponse<Buffer> httpResponse;
  @Mock NotificationConfig notificationConfig;

  GoAlertOnCallProvider provider;

  @BeforeEach
  void setUp() {
    provider = new GoAlertOnCallProvider(webClient, notificationConfig);
  }

  private void setupConfig(String url, String apiKey, String userApiKey, String serviceId) {
    GoAlertConfig goAlertConfig = new GoAlertConfig();
    goAlertConfig.setGoAlertUrl(url);
    goAlertConfig.setGoAlertApiKey(apiKey);
    goAlertConfig.setGoAlertUserApiKey(userApiKey);
    goAlertConfig.setGoAlertServiceId(serviceId);

    IncidentConfig incidentConfig = new IncidentConfig();
    incidentConfig.setGoAlert(goAlertConfig);
    when(notificationConfig.getIncidentConfig()).thenReturn(incidentConfig);
  }

  private void mockWebClientPost(JsonObject responseBody, int statusCode) {
    when(webClient.postAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
    when(httpRequest.rxSendJsonObject(any(JsonObject.class))).thenReturn(Single.just(httpResponse));
    when(httpResponse.statusCode()).thenReturn(statusCode);
    when(httpResponse.bodyAsJsonObject()).thenReturn(responseBody);
    when(httpResponse.bodyAsString()).thenReturn(responseBody != null ? responseBody.encode() : "");
  }

  @Nested
  class ConfigValidation {

    @Test
    void shouldReturnEmptyWhenConfigIsNull() {
      IncidentConfig incidentConfig = new IncidentConfig();
      when(notificationConfig.getIncidentConfig()).thenReturn(incidentConfig);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenUrlIsNull() {
      setupConfig(null, API_KEY, USER_API_KEY, SERVICE_ID);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenServiceIdIsNull() {
      setupConfig(URL, API_KEY, USER_API_KEY, null);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class FetchOnCallUsers {

    @Test
    void shouldReturnEmptyWhenServiceQueryReturnsHttpError() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);
      mockWebClientPost(new JsonObject().put("error", "bad request"), 400);

      provider.getOnCallUsers()
          .test()
          .assertError(RuntimeException.class);
    }

    @Test
    void shouldReturnEmptyWhenDataIsNull() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);
      JsonObject response = new JsonObject()
          .put("errors", new JsonArray().add(new JsonObject().put("message", "some error")))
          .putNull("data");
      mockWebClientPost(response, 200);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenServiceIsNull() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);
      JsonObject response = new JsonObject()
          .put("data", new JsonObject());
      mockWebClientPost(response, 200);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenOnCallUsersIsEmpty() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);
      JsonObject response = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray())));
      mockWebClientPost(response, 200);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldFilterToStepZeroUsersOnly() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);

      JsonObject serviceResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray()
                      .add(new JsonObject()
                          .put("userID", USER_ID)
                          .put("userName", USER_NAME)
                          .put("stepNumber", 0))
                      .add(new JsonObject()
                          .put("userID", "user-xyz")
                          .put("userName", "BackupUser")
                          .put("stepNumber", 1)))));

      JsonObject userResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("user", new JsonObject()
                  .put("id", USER_ID)
                  .put("name", USER_NAME)
                  .put("email", USER_EMAIL)));

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
          .thenReturn(Single.just(httpResponse))
          .thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsJsonObject())
          .thenReturn(serviceResponse)
          .thenReturn(userResponse);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getName()).isEqualTo(USER_NAME);
      assertThat(result.get(0).getEmail()).isEqualTo(USER_EMAIL);
    }

    @Test
    void shouldReturnEmptyWhenNoStepZeroUsers() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);
      JsonObject response = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray()
                      .add(new JsonObject()
                          .put("userID", USER_ID)
                          .put("userName", USER_NAME)
                          .put("stepNumber", 1)))));
      mockWebClientPost(response, 200);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class ServiceIdOverride {

    @Test
    void shouldUseProvidedServiceIdOverConfigDefault() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);

      String customServiceId = "custom-svc-override";

      JsonObject serviceResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray()
                      .add(new JsonObject()
                          .put("userID", USER_ID)
                          .put("userName", USER_NAME)
                          .put("stepNumber", 0)))));

      JsonObject userResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("user", new JsonObject()
                  .put("id", USER_ID)
                  .put("name", USER_NAME)
                  .put("email", USER_EMAIL)));

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
          .thenReturn(Single.just(httpResponse))
          .thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsJsonObject())
          .thenReturn(serviceResponse)
          .thenReturn(userResponse);

      List<OnCallUser> result = provider.getOnCallUsers(customServiceId).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getEmail()).isEqualTo(USER_EMAIL);
    }

    @Test
    void shouldFallbackToConfigServiceIdWhenProvidedIsNull() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);

      JsonObject serviceResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray()
                      .add(new JsonObject()
                          .put("userID", USER_ID)
                          .put("userName", USER_NAME)
                          .put("stepNumber", 0)))));

      JsonObject userResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("user", new JsonObject()
                  .put("id", USER_ID)
                  .put("name", USER_NAME)
                  .put("email", USER_EMAIL)));

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
          .thenReturn(Single.just(httpResponse))
          .thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsJsonObject())
          .thenReturn(serviceResponse)
          .thenReturn(userResponse);

      List<OnCallUser> result = provider.getOnCallUsers(null).blockingGet();

      assertThat(result).hasSize(1);
    }

    @Test
    void shouldFallbackToConfigServiceIdWhenProvidedIsBlank() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);

      JsonObject serviceResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray()
                      .add(new JsonObject()
                          .put("userID", USER_ID)
                          .put("userName", USER_NAME)
                          .put("stepNumber", 0)))));

      JsonObject userResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("user", new JsonObject()
                  .put("id", USER_ID)
                  .put("name", USER_NAME)
                  .put("email", USER_EMAIL)));

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
          .thenReturn(Single.just(httpResponse))
          .thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsJsonObject())
          .thenReturn(serviceResponse)
          .thenReturn(userResponse);

      List<OnCallUser> result = provider.getOnCallUsers("  ").blockingGet();

      assertThat(result).hasSize(1);
    }

    @Test
    void shouldReturnEmptyWhenBothServiceIdsAreNull() {
      setupConfig(URL, API_KEY, USER_API_KEY, null);

      List<OnCallUser> result = provider.getOnCallUsers(null).blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldUseServiceApiKeyWhenUserApiKeyIsNull() {
      setupConfig(URL, API_KEY, null, SERVICE_ID);

      JsonObject serviceResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray()
                      .add(new JsonObject()
                          .put("userID", USER_ID)
                          .put("userName", USER_NAME)
                          .put("stepNumber", 0)))));

      JsonObject userResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("user", new JsonObject()
                  .put("id", USER_ID)
                  .put("name", USER_NAME)
                  .put("email", USER_EMAIL)));

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
          .thenReturn(Single.just(httpResponse))
          .thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsJsonObject())
          .thenReturn(serviceResponse)
          .thenReturn(userResponse);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).hasSize(1);
    }

    @Test
    void shouldHandleNullDataInUserResponse() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);

      JsonObject serviceResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray()
                      .add(new JsonObject()
                          .put("userID", USER_ID)
                          .put("userName", USER_NAME)
                          .put("stepNumber", 0)))));

      JsonObject userResponse = new JsonObject().putNull("data");

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
          .thenReturn(Single.just(httpResponse))
          .thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsJsonObject())
          .thenReturn(serviceResponse)
          .thenReturn(userResponse);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenServiceIdBlankAndConfigBlank() {
      setupConfig(URL, API_KEY, USER_API_KEY, "  ");

      List<OnCallUser> result = provider.getOnCallUsers("").blockingGet();

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class EmailResolution {

    @Test
    void shouldSkipUserWhenEmailFetchFails() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);

      JsonObject serviceResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray()
                      .add(new JsonObject()
                          .put("userID", USER_ID)
                          .put("userName", USER_NAME)
                          .put("stepNumber", 0)))));

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
          .thenReturn(Single.just(httpResponse))
          .thenReturn(Single.error(new RuntimeException("connection refused")));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsJsonObject()).thenReturn(serviceResponse);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldSkipUserWhenEmailIsEmpty() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);

      JsonObject serviceResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray()
                      .add(new JsonObject()
                          .put("userID", USER_ID)
                          .put("userName", USER_NAME)
                          .put("stepNumber", 0)))));

      JsonObject userResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("user", new JsonObject()
                  .put("id", USER_ID)
                  .put("name", USER_NAME)
                  .put("email", "")));

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
          .thenReturn(Single.just(httpResponse))
          .thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsJsonObject())
          .thenReturn(serviceResponse)
          .thenReturn(userResponse);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldResolveEmailSuccessfully() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);

      JsonObject serviceResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray()
                      .add(new JsonObject()
                          .put("userID", USER_ID)
                          .put("userName", USER_NAME)
                          .put("stepNumber", 0)))));

      JsonObject userResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("user", new JsonObject()
                  .put("id", USER_ID)
                  .put("name", USER_NAME)
                  .put("email", USER_EMAIL)));

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
          .thenReturn(Single.just(httpResponse))
          .thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsJsonObject())
          .thenReturn(serviceResponse)
          .thenReturn(userResponse);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getName()).isEqualTo(USER_NAME);
      assertThat(result.get(0).getEmail()).isEqualTo(USER_EMAIL);
    }

    @Test
    void shouldHandleNullUserInResponse() {
      setupConfig(URL, API_KEY, USER_API_KEY, SERVICE_ID);

      JsonObject serviceResponse = new JsonObject()
          .put("data", new JsonObject()
              .put("service", new JsonObject()
                  .put("onCallUsers", new JsonArray()
                      .add(new JsonObject()
                          .put("userID", USER_ID)
                          .put("userName", USER_NAME)
                          .put("stepNumber", 0)))));

      JsonObject userResponse = new JsonObject()
          .put("data", new JsonObject());

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any(JsonObject.class)))
          .thenReturn(Single.just(httpResponse))
          .thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsJsonObject())
          .thenReturn(serviceResponse)
          .thenReturn(userResponse);

      List<OnCallUser> result = provider.getOnCallUsers().blockingGet();

      assertThat(result).isEmpty();
    }
  }
}
