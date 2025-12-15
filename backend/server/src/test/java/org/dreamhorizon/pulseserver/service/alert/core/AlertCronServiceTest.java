package org.dreamhorizon.pulseserver.service.alert.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.resources.alert.models.AddAlertToCronManager;
import org.dreamhorizon.pulseserver.resources.alert.models.DeleteAlertFromCronManager;
import org.dreamhorizon.pulseserver.resources.alert.models.UpdateAlertInCronManager;
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
class AlertCronServiceTest {

  @Mock
  private WebClient webClient;

  @Mock
  private ApplicationConfig applicationConfig;

  @Mock
  private HttpRequest<Buffer> httpRequest;

  @Mock
  private HttpResponse<Buffer> httpResponse;

  private AlertCronService alertCronService;

  private static final String CRON_MANAGER_BASE_URL = "http://localhost:8080/cron";

  @BeforeEach
  void setUp() {
    alertCronService = new AlertCronService(webClient, applicationConfig);
    when(applicationConfig.getCronManagerBaseUrl()).thenReturn(CRON_MANAGER_BASE_URL);
  }

  @Nested
  class TestCreateAlertCron {

    @Test
    void shouldCreateAlertCronSuccessfully() {
      AddAlertToCronManager cron = new AddAlertToCronManager(1, 60, "http://localhost/alert/1");

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJson(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);

      Boolean result = alertCronService.createAlertCron(cron).blockingGet();

      assertTrue(result);
      verify(webClient).postAbs(CRON_MANAGER_BASE_URL);
      verify(httpRequest).rxSendJson(cron);
    }

    @Test
    void shouldReturnFalseWhenStatusCodeIsNot200() {
      AddAlertToCronManager cron = new AddAlertToCronManager(1, 60, "http://localhost/alert/1");

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJson(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(500);

      Boolean result = alertCronService.createAlertCron(cron).blockingGet();

      assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenStatusCodeIs404() {
      AddAlertToCronManager cron = new AddAlertToCronManager(1, 60, "http://localhost/alert/1");

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJson(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(404);

      Boolean result = alertCronService.createAlertCron(cron).blockingGet();

      assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenStatusCodeIs201() {
      // 201 Created should return false since we only check for 200
      AddAlertToCronManager cron = new AddAlertToCronManager(1, 60, "http://localhost/alert/1");

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJson(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(201);

      Boolean result = alertCronService.createAlertCron(cron).blockingGet();

      assertFalse(result);
    }
  }

  @Nested
  class TestDeleteAlertCron {

    @Test
    void shouldDeleteAlertCronSuccessfully() {
      DeleteAlertFromCronManager cron = new DeleteAlertFromCronManager(1, 60);

      when(webClient.deleteAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJson(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);

      Boolean result = alertCronService.deleteAlertCron(cron).blockingGet();

      assertTrue(result);
      verify(webClient).deleteAbs(CRON_MANAGER_BASE_URL);
      verify(httpRequest).rxSendJson(cron);
    }

    @Test
    void shouldReturnFalseWhenDeleteStatusCodeIsNot200() {
      DeleteAlertFromCronManager cron = new DeleteAlertFromCronManager(1, 60);

      when(webClient.deleteAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJson(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(500);

      Boolean result = alertCronService.deleteAlertCron(cron).blockingGet();

      assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenDeleteStatusCodeIs404() {
      DeleteAlertFromCronManager cron = new DeleteAlertFromCronManager(1, 60);

      when(webClient.deleteAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJson(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(404);

      Boolean result = alertCronService.deleteAlertCron(cron).blockingGet();

      assertFalse(result);
    }
  }

  @Nested
  class TestUpdateAlertCron {

    @Test
    void shouldUpdateAlertCronSuccessfully() {
      UpdateAlertInCronManager cron = new UpdateAlertInCronManager(1, 120, 60, "http://localhost/alert/1");

      when(webClient.putAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJson(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);

      Boolean result = alertCronService.updateAlertCron(cron).blockingGet();

      assertTrue(result);
      verify(webClient).putAbs(CRON_MANAGER_BASE_URL);
      verify(httpRequest).rxSendJson(cron);
    }

    @Test
    void shouldReturnFalseWhenUpdateStatusCodeIsNot200() {
      UpdateAlertInCronManager cron = new UpdateAlertInCronManager(1, 120, 60, "http://localhost/alert/1");

      when(webClient.putAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJson(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(500);

      Boolean result = alertCronService.updateAlertCron(cron).blockingGet();

      assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenUpdateStatusCodeIs404() {
      UpdateAlertInCronManager cron = new UpdateAlertInCronManager(1, 120, 60, "http://localhost/alert/1");

      when(webClient.putAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.rxSendJson(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(404);

      Boolean result = alertCronService.updateAlertCron(cron).blockingGet();

      assertFalse(result);
    }
  }

  @Nested
  class TestServiceGettersAndSetters {

    @Test
    void shouldGetWebClient() {
      assertEquals(webClient, alertCronService.getD11WebClient());
    }

    @Test
    void shouldGetApplicationConfig() {
      assertEquals(applicationConfig, alertCronService.getApplicationConfig());
    }

    @Test
    void shouldHaveCorrectToString() {
      String toString = alertCronService.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("AlertCronService"));
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AlertCronService service1 = new AlertCronService(webClient, applicationConfig);
      AlertCronService service2 = new AlertCronService(webClient, applicationConfig);

      assertEquals(service1, service2);
      assertEquals(service1.hashCode(), service2.hashCode());
    }
  }

  @Nested
  class TestAddAlertToCronManagerModel {

    @Test
    void shouldCreateAddAlertToCronManagerWithAllArgs() {
      AddAlertToCronManager model = new AddAlertToCronManager(1, 60, "http://test.url");

      assertEquals(1, model.getId());
      assertEquals(60, model.getInterval());
      assertEquals("http://test.url", model.getUrl());
    }

    @Test
    void shouldCreateAddAlertToCronManagerWithNoArgs() {
      AddAlertToCronManager model = new AddAlertToCronManager();

      assertNotNull(model);
    }

    @Test
    void shouldSetAddAlertToCronManagerFields() {
      AddAlertToCronManager model = new AddAlertToCronManager();
      model.setId(2);
      model.setInterval(120);
      model.setUrl("http://new.url");

      assertEquals(2, model.getId());
      assertEquals(120, model.getInterval());
      assertEquals("http://new.url", model.getUrl());
    }

    @Test
    void shouldHaveCorrectToString() {
      AddAlertToCronManager model = new AddAlertToCronManager(1, 60, "http://test.url");
      String toString = model.toString();

      assertTrue(toString.contains("id=1"));
      assertTrue(toString.contains("interval=60"));
      assertTrue(toString.contains("url=http://test.url"));
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      AddAlertToCronManager model1 = new AddAlertToCronManager(1, 60, "http://test.url");
      AddAlertToCronManager model2 = new AddAlertToCronManager(1, 60, "http://test.url");
      AddAlertToCronManager model3 = new AddAlertToCronManager(2, 60, "http://test.url");

      assertEquals(model1, model2);
      assertEquals(model1.hashCode(), model2.hashCode());
      assertFalse(model1.equals(model3));
    }
  }

  @Nested
  class TestDeleteAlertFromCronManagerModel {

    @Test
    void shouldCreateDeleteAlertFromCronManagerWithAllArgs() {
      DeleteAlertFromCronManager model = new DeleteAlertFromCronManager(1, 60);

      assertEquals(1, model.getId());
      assertEquals(60, model.getInterval());
    }

    @Test
    void shouldCreateDeleteAlertFromCronManagerWithNoArgs() {
      DeleteAlertFromCronManager model = new DeleteAlertFromCronManager();

      assertNotNull(model);
    }

    @Test
    void shouldSetDeleteAlertFromCronManagerFields() {
      DeleteAlertFromCronManager model = new DeleteAlertFromCronManager();
      model.setId(2);
      model.setInterval(120);

      assertEquals(2, model.getId());
      assertEquals(120, model.getInterval());
    }

    @Test
    void shouldHaveCorrectToStringForDelete() {
      DeleteAlertFromCronManager model = new DeleteAlertFromCronManager(1, 60);
      String toString = model.toString();

      assertTrue(toString.contains("id=1"));
      assertTrue(toString.contains("interval=60"));
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCodeForDelete() {
      DeleteAlertFromCronManager model1 = new DeleteAlertFromCronManager(1, 60);
      DeleteAlertFromCronManager model2 = new DeleteAlertFromCronManager(1, 60);
      DeleteAlertFromCronManager model3 = new DeleteAlertFromCronManager(2, 60);

      assertEquals(model1, model2);
      assertEquals(model1.hashCode(), model2.hashCode());
      assertFalse(model1.equals(model3));
    }
  }

  @Nested
  class TestUpdateAlertInCronManagerModel {

    @Test
    void shouldCreateUpdateAlertInCronManagerWithAllArgs() {
      UpdateAlertInCronManager model = new UpdateAlertInCronManager(1, 120, 60, "http://test.url");

      assertEquals(1, model.getId());
      assertEquals(120, model.getNewInterval());
      assertEquals(60, model.getOldInterval());
      assertEquals("http://test.url", model.getUrl());
    }

    @Test
    void shouldCreateUpdateAlertInCronManagerWithNoArgs() {
      UpdateAlertInCronManager model = new UpdateAlertInCronManager();

      assertNotNull(model);
    }

    @Test
    void shouldSetUpdateAlertInCronManagerFields() {
      UpdateAlertInCronManager model = new UpdateAlertInCronManager();
      model.setId(2);
      model.setNewInterval(180);
      model.setOldInterval(120);
      model.setUrl("http://new.url");

      assertEquals(2, model.getId());
      assertEquals(180, model.getNewInterval());
      assertEquals(120, model.getOldInterval());
      assertEquals("http://new.url", model.getUrl());
    }

    @Test
    void shouldHaveCorrectToStringForUpdate() {
      UpdateAlertInCronManager model = new UpdateAlertInCronManager(1, 120, 60, "http://test.url");
      String toString = model.toString();

      assertTrue(toString.contains("id=1"));
      assertTrue(toString.contains("newInterval=120"));
      assertTrue(toString.contains("oldInterval=60"));
      assertTrue(toString.contains("url=http://test.url"));
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCodeForUpdate() {
      UpdateAlertInCronManager model1 = new UpdateAlertInCronManager(1, 120, 60, "http://test.url");
      UpdateAlertInCronManager model2 = new UpdateAlertInCronManager(1, 120, 60, "http://test.url");
      UpdateAlertInCronManager model3 = new UpdateAlertInCronManager(2, 120, 60, "http://test.url");

      assertEquals(model1, model2);
      assertEquals(model1.hashCode(), model2.hashCode());
      assertFalse(model1.equals(model3));
    }
  }

  @Nested
  class TestApplicationConfigModel {

    @Test
    void shouldCreateApplicationConfigWithAllArgs() {
      ApplicationConfig config = new ApplicationConfig(
          "http://cron.url",
          "http://service.url",
          30,
          "google-client-id",
          true,
          "jwt-secret",
          "http://webhook.url"
      );

      assertEquals("http://cron.url", config.getCronManagerBaseUrl());
      assertEquals("http://service.url", config.getServiceUrl());
      assertEquals(30, config.getShutdownGracePeriod());
      assertEquals("google-client-id", config.getGoogleOAuthClientId());
      assertTrue(config.getGoogleOAuthEnabled());
      assertEquals("jwt-secret", config.getJwtSecret());
      assertEquals("http://webhook.url", config.getWebhookUrl());
    }

    @Test
    void shouldCreateApplicationConfigWithNoArgs() {
      ApplicationConfig config = new ApplicationConfig();

      assertNotNull(config);
    }

    @Test
    void shouldSetApplicationConfigFields() {
      ApplicationConfig config = new ApplicationConfig();
      config.setCronManagerBaseUrl("http://new-cron.url");
      config.setServiceUrl("http://new-service.url");
      config.setShutdownGracePeriod(60);
      config.setGoogleOAuthClientId("new-client-id");
      config.setGoogleOAuthEnabled(false);
      config.setJwtSecret("new-jwt-secret");
      config.setWebhookUrl("http://new-webhook.url");

      assertEquals("http://new-cron.url", config.getCronManagerBaseUrl());
      assertEquals("http://new-service.url", config.getServiceUrl());
      assertEquals(60, config.getShutdownGracePeriod());
      assertEquals("new-client-id", config.getGoogleOAuthClientId());
      assertFalse(config.getGoogleOAuthEnabled());
      assertEquals("new-jwt-secret", config.getJwtSecret());
      assertEquals("http://new-webhook.url", config.getWebhookUrl());
    }

    @Test
    void shouldHaveCorrectToStringForApplicationConfig() {
      ApplicationConfig config = new ApplicationConfig(
          "http://cron.url",
          "http://service.url",
          30,
          "google-client-id",
          true,
          "jwt-secret",
          "http://webhook.url"
      );
      String toString = config.toString();

      assertTrue(toString.contains("cronManagerBaseUrl=http://cron.url"));
      assertTrue(toString.contains("serviceUrl=http://service.url"));
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCodeForApplicationConfig() {
      ApplicationConfig config1 = new ApplicationConfig(
          "http://cron.url", "http://service.url", 30, "client-id", true, "secret", "http://webhook.url"
      );
      ApplicationConfig config2 = new ApplicationConfig(
          "http://cron.url", "http://service.url", 30, "client-id", true, "secret", "http://webhook.url"
      );
      ApplicationConfig config3 = new ApplicationConfig(
          "http://different.url", "http://service.url", 30, "client-id", true, "secret", "http://webhook.url"
      );

      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());
      assertFalse(config1.equals(config3));
    }
  }
}
