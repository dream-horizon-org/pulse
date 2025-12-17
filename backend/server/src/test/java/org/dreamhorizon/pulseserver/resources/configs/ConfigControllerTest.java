package org.dreamhorizon.pulseserver.resources.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import jakarta.ws.rs.WebApplicationException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.GetScopeAndSdksResponse;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.resources.configs.models.RulesAndFeaturesResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.CreateConfigResponse;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.service.configs.models.FilterMode;
import org.dreamhorizon.pulseserver.service.configs.models.Scope;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;
import org.dreamhorizon.pulseserver.service.configs.models.rules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
class ConfigControllerTest {

  @Mock
  ConfigService configService;

  @Mock
  ApplicationConfig applicationConfig;

  ConfigController configController;

  final String userEmail = "test@dream11.com";

  @BeforeEach
  void setup() {
    configController = new ConfigController(configService, applicationConfig);
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetConfig {

    @Test
    void shouldGetConfigByVersion(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        Integer version = 1;
        PulseConfig mockConfig = PulseConfig.builder()
            .version(1L)
            .description("Test Config")
            .build();

        when(configService.getConfig(version)).thenReturn(Single.just(mockConfig));

        // When
        CompletionStage<Response<PulseConfig>> result = configController.getConfig(version);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertEquals(1L, resp.getData().getVersion());
            assertEquals("Test Config", resp.getData().getDescription());
            verify(configService, times(1)).getConfig(version);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldThrowExceptionWhenConfigNotFound(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        Integer version = 999;

        when(configService.getConfig(version))
            .thenReturn(Single.error(ServiceError.DATABASE_ERROR.getCustomException(
                "Config not found", "Config not found", 404)));

        // When
        CompletionStage<Response<PulseConfig>> result = configController.getConfig(version);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            WebApplicationException webException = (WebApplicationException) err;
            assertEquals(404, webException.getResponse().getStatus());
            verify(configService, times(1)).getConfig(version);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldHandleServiceError(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        Integer version = 1;

        when(configService.getConfig(version))
            .thenReturn(Single.error(ServiceError.DATABASE_ERROR.getCustomException(
                "Database error", "Database error", 500)));

        // When
        CompletionStage<Response<PulseConfig>> result = configController.getConfig(version);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            WebApplicationException webException = (WebApplicationException) err;
            assertEquals(500, webException.getResponse().getStatus());
          });
          testContext.completeNow();
        });
      });
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetActiveConfig {

    @Test
    void shouldGetActiveConfig(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig mockConfig = PulseConfig.builder()
            .version(5L)
            .description("Active Config")
            .build();

        when(configService.getActiveConfig()).thenReturn(Single.just(mockConfig));

        // When
        CompletionStage<Response<PulseConfig>> result = configController.getActiveConfig();

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertEquals(5L, resp.getData().getVersion());
            assertEquals("Active Config", resp.getData().getDescription());
            verify(configService, times(1)).getActiveConfig();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldHandleServiceErrorForActiveConfig(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        when(configService.getActiveConfig())
            .thenReturn(Single.error(ServiceError.DATABASE_ERROR.getCustomException(
                "No active config", "No active config", 404)));

        // When
        CompletionStage<Response<PulseConfig>> result = configController.getActiveConfig();

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            verify(configService, times(1)).getActiveConfig();
          });
          testContext.completeNow();
        });
      });
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestCreateConfig {

    @Test
    void shouldCreateConfigSuccessfully(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getInteraction().setCollectorUrl("http://custom-collector.example.com");
        pulseConfig.getInteraction().setConfigUrl("http://custom-config.example.com");

        PulseConfig createdConfig = PulseConfig.builder()
            .version(10L)
            .description(pulseConfig.getDescription())
            .sampling(pulseConfig.getSampling())
            .signals(pulseConfig.getSignals())
            .interaction(pulseConfig.getInteraction())
            .features(pulseConfig.getFeatures())
            .build();

        ArgumentCaptor<ConfigData> configDataCaptor = ArgumentCaptor.forClass(ConfigData.class);
        when(configService.createConfig(configDataCaptor.capture())).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertEquals(10L, resp.getData().getVersion());
            assertEquals(userEmail, configDataCaptor.getValue().getUser());
            verify(configService, times(1)).createConfig(any(ConfigData.class));
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultCollectorUrlWhenNull(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getInteraction().setCollectorUrl(null);
        pulseConfig.getInteraction().setConfigUrl("http://custom-config.example.com");

        when(applicationConfig.getOtelCollectorUrl()).thenReturn("http://default-collector.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 11L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals(11L, resp.getData().getVersion());
            assertEquals("http://default-collector.example.com", pulseConfig.getInteraction().getCollectorUrl());
            verify(applicationConfig, times(1)).getOtelCollectorUrl();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultCollectorUrlWhenBlank(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getInteraction().setCollectorUrl("   ");
        pulseConfig.getInteraction().setConfigUrl("http://custom-config.example.com");

        when(applicationConfig.getOtelCollectorUrl()).thenReturn("http://default-collector.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 12L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals("http://default-collector.example.com", pulseConfig.getInteraction().getCollectorUrl());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultConfigUrlWhenNull(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getInteraction().setCollectorUrl("http://custom-collector.example.com");
        pulseConfig.getInteraction().setConfigUrl(null);

        when(applicationConfig.getInteractionConfigUrl()).thenReturn("http://default-config.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 13L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals("http://default-config.example.com", pulseConfig.getInteraction().getConfigUrl());
            verify(applicationConfig, times(1)).getInteractionConfigUrl();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultConfigUrlWhenBlank(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getInteraction().setCollectorUrl("http://custom-collector.example.com");
        pulseConfig.getInteraction().setConfigUrl("");

        when(applicationConfig.getInteractionConfigUrl()).thenReturn("http://default-config.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 14L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals("http://default-config.example.com", pulseConfig.getInteraction().getConfigUrl());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyBothDefaultUrlsWhenBothNullOrBlank(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getInteraction().setCollectorUrl(null);
        pulseConfig.getInteraction().setConfigUrl("");

        when(applicationConfig.getOtelCollectorUrl()).thenReturn("http://default-collector.example.com");
        when(applicationConfig.getInteractionConfigUrl()).thenReturn("http://default-config.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 15L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals("http://default-collector.example.com", pulseConfig.getInteraction().getCollectorUrl());
            assertEquals("http://default-config.example.com", pulseConfig.getInteraction().getConfigUrl());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldNotApplyDefaultsWhenInteractionIsNull(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.setInteraction(null);

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 16L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals(16L, resp.getData().getVersion());
            assertNull(pulseConfig.getInteraction());
            // Verify applicationConfig methods were not called
            verify(applicationConfig, times(0)).getOtelCollectorUrl();
            verify(applicationConfig, times(0)).getInteractionConfigUrl();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultLogsCollectorUrlWhenNull(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getSignals().setLogsCollectorUrl(null);

        when(applicationConfig.getLogsCollectorUrl()).thenReturn("http://default-logs.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 30L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals(30L, resp.getData().getVersion());
            assertEquals("http://default-logs.example.com", pulseConfig.getSignals().getLogsCollectorUrl());
            verify(applicationConfig, times(1)).getLogsCollectorUrl();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultLogsCollectorUrlWhenBlank(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getSignals().setLogsCollectorUrl("   ");

        when(applicationConfig.getLogsCollectorUrl()).thenReturn("http://default-logs.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 31L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals("http://default-logs.example.com", pulseConfig.getSignals().getLogsCollectorUrl());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultMetricCollectorUrlWhenNull(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getSignals().setMetricCollectorUrl(null);

        when(applicationConfig.getMetricCollectorUrl()).thenReturn("http://default-metrics.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 32L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals(32L, resp.getData().getVersion());
            assertEquals("http://default-metrics.example.com", pulseConfig.getSignals().getMetricCollectorUrl());
            verify(applicationConfig, times(1)).getMetricCollectorUrl();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultMetricCollectorUrlWhenBlank(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getSignals().setMetricCollectorUrl("");

        when(applicationConfig.getMetricCollectorUrl()).thenReturn("http://default-metrics.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 33L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals("http://default-metrics.example.com", pulseConfig.getSignals().getMetricCollectorUrl());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultSpanCollectorUrlWhenNull(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getSignals().setSpanCollectorUrl(null);

        when(applicationConfig.getSpanCollectorUrl()).thenReturn("http://default-spans.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 34L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals(34L, resp.getData().getVersion());
            assertEquals("http://default-spans.example.com", pulseConfig.getSignals().getSpanCollectorUrl());
            verify(applicationConfig, times(1)).getSpanCollectorUrl();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultSpanCollectorUrlWhenBlank(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getSignals().setSpanCollectorUrl("  ");

        when(applicationConfig.getSpanCollectorUrl()).thenReturn("http://default-spans.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 35L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals("http://default-spans.example.com", pulseConfig.getSignals().getSpanCollectorUrl());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyAllSignalsDefaultsWhenAllNull(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getSignals().setLogsCollectorUrl(null);
        pulseConfig.getSignals().setMetricCollectorUrl(null);
        pulseConfig.getSignals().setSpanCollectorUrl(null);

        when(applicationConfig.getLogsCollectorUrl()).thenReturn("http://default-logs.example.com");
        when(applicationConfig.getMetricCollectorUrl()).thenReturn("http://default-metrics.example.com");
        when(applicationConfig.getSpanCollectorUrl()).thenReturn("http://default-spans.example.com");

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 36L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals("http://default-logs.example.com", pulseConfig.getSignals().getLogsCollectorUrl());
            assertEquals("http://default-metrics.example.com", pulseConfig.getSignals().getMetricCollectorUrl());
            assertEquals("http://default-spans.example.com", pulseConfig.getSignals().getSpanCollectorUrl());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldNotApplySignalsDefaultsWhenSignalsIsNull(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.setSignals(null);

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 37L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals(37L, resp.getData().getVersion());
            assertNull(pulseConfig.getSignals());
            // Verify signals config methods were not called
            verify(applicationConfig, times(0)).getLogsCollectorUrl();
            verify(applicationConfig, times(0)).getMetricCollectorUrl();
            verify(applicationConfig, times(0)).getSpanCollectorUrl();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldHandleServiceErrorOnCreate(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        PulseConfig pulseConfig = createValidPulseConfig();
        pulseConfig.getInteraction().setCollectorUrl("http://collector.example.com");
        pulseConfig.getInteraction().setConfigUrl("http://config.example.com");

        when(configService.createConfig(any(ConfigData.class)))
            .thenReturn(Single.error(ServiceError.DATABASE_ERROR.getCustomException(
                "Creation failed", "Creation failed", 500)));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            WebApplicationException webException = (WebApplicationException) err;
            assertEquals(500, webException.getResponse().getStatus());
          });
          testContext.completeNow();
        });
      });
    }

    private PulseConfig createValidPulseConfig() {
      return PulseConfig.builder()
          .description("Test Config")
          .sampling(PulseConfig.SamplingConfig.builder()
              .defaultSampling(PulseConfig.DefaultSampling.builder()
                  .sessionSampleRate(1.0)
                  .build())
              .rules(List.of())
              .build())
          .signals(PulseConfig.SignalsConfig.builder()
              .scheduleDurationMs(5000)
              .logsCollectorUrl("http://logs.example.com")
              .metricCollectorUrl("http://metrics.example.com")
              .spanCollectorUrl("http://spans.example.com")
              .attributesToDrop(List.of())
              .filters(PulseConfig.FilterConfig.builder()
                  .mode(FilterMode.blacklist)
                  .values(List.of())
                  .build())
              .build())
          .interaction(PulseConfig.InteractionConfig.builder()
              .collectorUrl("http://interaction-collector.example.com")
              .configUrl("http://interaction-config.example.com")
              .beforeInitQueueSize(100)
              .build())
          .features(List.of(
              PulseConfig.FeatureConfig.builder()
                  .featureName(Features.java_crash)
                  .enabled(true)
                  .sessionSampleRate(1.0)
                  .sdks(List.of())
                  .build()
          ))
          .build();
    }

    /**
     * Creates a fully populated PulseConfig to exercise all mapper branches
     */
    private PulseConfig createFullyPopulatedPulseConfig() {
      return PulseConfig.builder()
          .description("Full Config")
          .sampling(PulseConfig.SamplingConfig.builder()
              .defaultSampling(PulseConfig.DefaultSampling.builder()
                  .sessionSampleRate(0.5)
                  .build())
              .rules(Arrays.asList(
                  PulseConfig.SamplingRule.builder()
                      .name(rules.os_version)
                      .sdks(Arrays.asList(Sdk.android_java))
                      .value("14")
                      .sessionSampleRate(1.0)
                      .build(),
                  PulseConfig.SamplingRule.builder()
                      .name(rules.app_version)
                      .sdks(Arrays.asList(Sdk.ios_native))
                      .value("2.0.0")
                      .sessionSampleRate(0.8)
                      .build()
              ))
              .criticalEventPolicies(PulseConfig.CriticalEventPolicies.builder()
                  .alwaysSend(Arrays.asList(
                      PulseConfig.CriticalPolicyRule.builder()
                          .name("crashEvent")
                          .props(Arrays.asList(
                              PulseConfig.EventPropMatch.builder()
                                  .name("severity")
                                  .value("critical")
                                  .build()
                          ))
                          .scopes(Arrays.asList(Scope.logs))
                          .sdks(Arrays.asList(Sdk.android_java, Sdk.ios_native))
                          .build()
                  ))
                  .build())
              .criticalSessionPolicies(PulseConfig.CriticalSessionPolicies.builder()
                  .alwaysSend(Arrays.asList(
                      PulseConfig.CriticalPolicyRule.builder()
                          .name("vipUser")
                          .props(Arrays.asList(
                              PulseConfig.EventPropMatch.builder()
                                  .name("userType")
                                  .value("vip")
                                  .build()
                          ))
                          .scopes(Arrays.asList(Scope.traces, Scope.baggage))
                          .sdks(Arrays.asList(Sdk.ios_rn))
                          .build()
                  ))
                  .build())
              .build())
          .signals(PulseConfig.SignalsConfig.builder()
              .scheduleDurationMs(10000)
              .logsCollectorUrl("http://logs.example.com")
              .metricCollectorUrl("http://metrics.example.com")
              .spanCollectorUrl("http://spans.example.com")
              .attributesToDrop(Arrays.asList(
                  PulseConfig.EventFilter.builder()
                      .name("sensitiveAttr1")
                      .props(List.of())
                      .scopes(Arrays.asList(Scope.logs))
                      .sdks(Arrays.asList(Sdk.android_java))
                      .build(),
                  PulseConfig.EventFilter.builder()
                      .name("sensitiveAttr2")
                      .props(List.of())
                      .scopes(Arrays.asList(Scope.logs))
                      .sdks(Arrays.asList(Sdk.android_java))
                      .build()
              ))
              .filters(PulseConfig.FilterConfig.builder()
                  .mode(FilterMode.whitelist)
                  .values(Arrays.asList(
                      PulseConfig.EventFilter.builder()
                          .name("event1")
                          .props(Arrays.asList(
                              PulseConfig.EventPropMatch.builder()
                                  .name("propName")
                                  .value("propValue.*")
                                  .build()
                          ))
                          .scopes(Arrays.asList(Scope.logs, Scope.traces))
                          .sdks(Arrays.asList(Sdk.android_java, Sdk.ios_native))
                          .build()
                  ))
                  .build())
              .build())
          .interaction(PulseConfig.InteractionConfig.builder()
              .collectorUrl("http://interaction-collector.example.com")
              .configUrl("http://interaction-config.example.com")
              .beforeInitQueueSize(200)
              .build())
          .features(Arrays.asList(
              PulseConfig.FeatureConfig.builder()
                  .featureName(Features.java_crash)
                  .enabled(true)
                  .sessionSampleRate(1.0)
                  .sdks(Arrays.asList(Sdk.android_java))
                  .build(),
              PulseConfig.FeatureConfig.builder()
                  .featureName(Features.java_anr)
                  .enabled(true)
                  .sessionSampleRate(0.9)
                  .sdks(Arrays.asList(Sdk.android_java, Sdk.ios_native))
                  .build(),
              PulseConfig.FeatureConfig.builder()
                  .featureName(Features.interaction)
                  .enabled(false)
                  .sessionSampleRate(0.5)
                  .sdks(Arrays.asList(Sdk.android_rn))
                  .build()
          ))
          .build();
    }

    private PulseConfig createPulseConfigWithVersion(PulseConfig source, long version) {
      return PulseConfig.builder()
          .version(version)
          .description(source.getDescription())
          .sampling(source.getSampling())
          .signals(source.getSignals())
          .interaction(source.getInteraction())
          .features(source.getFeatures())
          .build();
    }

    @Test
    void shouldCreateConfigWithNullNestedObjects(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given - Test with null nested objects to cover null-check branches in mapper
        PulseConfig pulseConfig = PulseConfig.builder()
            .description("Minimal Config")
            .sampling(null)
            .signals(null)
            .interaction(null)
            .features(null)
            .build();

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 21L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertEquals(21L, resp.getData().getVersion());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldCreateConfigWithEmptyLists(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given - Test with empty lists to cover list handling branches
        PulseConfig pulseConfig = PulseConfig.builder()
            .description("Empty Lists Config")
            .sampling(PulseConfig.SamplingConfig.builder()
                .defaultSampling(null)
                .rules(null)
                .criticalEventPolicies(null)
                .criticalSessionPolicies(null)
                .build())
            .signals(PulseConfig.SignalsConfig.builder()
                .scheduleDurationMs(1000)
                .logsCollectorUrl("http://logs.example.com")
                .metricCollectorUrl("http://metrics.example.com")
                .spanCollectorUrl("http://spans.example.com")
                .attributesToDrop(null)
                .filters(PulseConfig.FilterConfig.builder()
                    .mode(FilterMode.blacklist)
                    .values(null)
                    .build())
                .build())
            .interaction(PulseConfig.InteractionConfig.builder()
                .collectorUrl("http://interaction.example.com")
                .configUrl("http://config.example.com")
                .beforeInitQueueSize(50)
                .build())
            .features(List.of())
            .build();

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 22L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals(22L, resp.getData().getVersion());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldCreateConfigWithPartiallyPopulatedFilterEvents(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given - Test with filter events that have null props/scope/sdks
        PulseConfig pulseConfig = PulseConfig.builder()
            .description("Partial Filter Config")
            .sampling(PulseConfig.SamplingConfig.builder()
                .defaultSampling(PulseConfig.DefaultSampling.builder()
                    .sessionSampleRate(1.0)
                    .build())
                .rules(List.of())
                .criticalEventPolicies(PulseConfig.CriticalEventPolicies.builder()
                    .alwaysSend(Arrays.asList(
                        PulseConfig.CriticalPolicyRule.builder()
                            .name("critical1")
                            .props(null)
                            .scopes(null)
                            .sdks(null)
                            .build()
                    ))
                    .build())
                .criticalSessionPolicies(PulseConfig.CriticalSessionPolicies.builder()
                    .alwaysSend(Arrays.asList(
                        PulseConfig.CriticalPolicyRule.builder()
                            .name("session1")
                            .props(null)
                            .scopes(null)
                            .sdks(null)
                            .build()
                    ))
                    .build())
                .build())
            .signals(PulseConfig.SignalsConfig.builder()
                .scheduleDurationMs(5000)
                .logsCollectorUrl("http://logs.example.com")
                .metricCollectorUrl("http://metrics.example.com")
                .spanCollectorUrl("http://spans.example.com")
                .attributesToDrop(List.of())
                .filters(PulseConfig.FilterConfig.builder()
                    .mode(FilterMode.whitelist)
                    .values(Arrays.asList(
                        PulseConfig.EventFilter.builder()
                            .name("event1")
                            .props(null)
                            .scopes(null)
                            .sdks(null)
                            .build()
                    ))
                    .build())
                .build())
            .interaction(PulseConfig.InteractionConfig.builder()
                .collectorUrl("http://interaction.example.com")
                .configUrl("http://config.example.com")
                .beforeInitQueueSize(100)
                .build())
            .features(Arrays.asList(
                PulseConfig.FeatureConfig.builder()
                    .featureName(Features.java_crash)
                    .enabled(true)
                    .sessionSampleRate(null)
                    .sdks(null)
                    .build()
            ))
            .build();

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 23L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals(23L, resp.getData().getVersion());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldCreateConfigWithSamplingRulesHavingNullSdks(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given - Test sampling rules with null sdks
        PulseConfig pulseConfig = PulseConfig.builder()
            .description("Sampling Rules Config")
            .sampling(PulseConfig.SamplingConfig.builder()
                .defaultSampling(PulseConfig.DefaultSampling.builder()
                    .sessionSampleRate(0.5)
                    .build())
                .rules(Arrays.asList(
                    PulseConfig.SamplingRule.builder()
                        .name(rules.os_version)
                        .sdks(null)
                        .value("14")
                        .sessionSampleRate(1.0)
                        .build()
                ))
                .build())
            .signals(PulseConfig.SignalsConfig.builder()
                .scheduleDurationMs(5000)
                .logsCollectorUrl("http://logs.example.com")
                .metricCollectorUrl("http://metrics.example.com")
                .spanCollectorUrl("http://spans.example.com")
                .attributesToDrop(List.of())
                .filters(PulseConfig.FilterConfig.builder()
                    .mode(FilterMode.blacklist)
                    .values(List.of())
                    .build())
                .build())
            .interaction(PulseConfig.InteractionConfig.builder()
                .collectorUrl("http://interaction.example.com")
                .configUrl("http://config.example.com")
                .beforeInitQueueSize(100)
                .build())
            .features(List.of())
            .build();

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 24L);

        when(configService.createConfig(any(ConfigData.class))).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals(24L, resp.getData().getVersion());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldCreateConfigWithFullyPopulatedData(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given - Use fully populated config to exercise all mapper branches
        PulseConfig pulseConfig = createFullyPopulatedPulseConfig();

        PulseConfig createdConfig = createPulseConfigWithVersion(pulseConfig, 20L);

        ArgumentCaptor<ConfigData> configDataCaptor = ArgumentCaptor.forClass(ConfigData.class);
        when(configService.createConfig(configDataCaptor.capture())).thenReturn(Single.just(createdConfig));

        // When
        CompletionStage<Response<CreateConfigResponse>> result =
            configController.createConfig(userEmail, pulseConfig);

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertEquals(20L, resp.getData().getVersion());
            
            // Verify the mapper converted all nested objects
            ConfigData capturedData = configDataCaptor.getValue();
            assertNotNull(capturedData);
            assertEquals(userEmail, capturedData.getUser());
            assertEquals("Full Config", capturedData.getDescription());
            assertNotNull(capturedData.getSampling());
            assertNotNull(capturedData.getSignals());
            assertNotNull(capturedData.getSignals().getFilters());
            assertNotNull(capturedData.getInteraction());
            assertNotNull(capturedData.getFeatures());
            assertEquals(3, capturedData.getFeatures().size());
          });
          testContext.completeNow();
        });
      });
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetConfigDescription {

    @Test
    void shouldGetAllConfigDetails(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        AllConfigdetails mockDetails = AllConfigdetails.builder()
            .configDetails(Arrays.asList(
                AllConfigdetails.Configdetails.builder()
                    .version(1L)
                    .description("Config 1")
                    .createdBy("user1")
                    .createdAt("2024-01-01 00:00:00")
                    .isactive(true)
                    .build(),
                AllConfigdetails.Configdetails.builder()
                    .version(2L)
                    .description("Config 2")
                    .createdBy("user2")
                    .createdAt("2024-01-02 00:00:00")
                    .isactive(false)
                    .build()
            ))
            .build();

        when(configService.getAllConfigDetails()).thenReturn(Single.just(mockDetails));

        // When
        CompletionStage<Response<AllConfigdetails>> result = configController.getConfigDescription();

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertEquals(2, resp.getData().getConfigDetails().size());
            assertEquals(1L, resp.getData().getConfigDetails().get(0).getVersion());
            assertEquals("Config 1", resp.getData().getConfigDetails().get(0).getDescription());
            verify(configService, times(1)).getAllConfigDetails();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldHandleEmptyConfigDetails(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        AllConfigdetails mockDetails = AllConfigdetails.builder()
            .configDetails(List.of())
            .build();

        when(configService.getAllConfigDetails()).thenReturn(Single.just(mockDetails));

        // When
        CompletionStage<Response<AllConfigdetails>> result = configController.getConfigDescription();

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertEquals(0, resp.getData().getConfigDetails().size());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldHandleServiceErrorForConfigDetails(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        when(configService.getAllConfigDetails())
            .thenReturn(Single.error(ServiceError.DATABASE_ERROR.getCustomException(
                "Database error", "Database error", 500)));

        // When
        CompletionStage<Response<AllConfigdetails>> result = configController.getConfigDescription();

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
          });
          testContext.completeNow();
        });
      });
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetFeatures {

    @Test
    void shouldGetRulesAndFeatures(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        RulesAndFeaturesResponse mockResponse = RulesAndFeaturesResponse.builder()
            .rules(Arrays.asList("os_version", "app_version", "country"))
            .features(Arrays.asList("java_crash", "native_crash", "anr"))
            .build();

        when(configService.getRulesandFeatures()).thenReturn(Single.just(mockResponse));

        // When
        CompletionStage<Response<RulesAndFeaturesResponse>> result = configController.getFeatures();

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertNotNull(resp.getData().getRules());
            assertNotNull(resp.getData().getFeatures());
            assertEquals(3, resp.getData().getRules().size());
            assertEquals(3, resp.getData().getFeatures().size());
            verify(configService, times(1)).getRulesandFeatures();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldHandleServiceErrorForFeatures(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        when(configService.getRulesandFeatures())
            .thenReturn(Single.error(ServiceError.DATABASE_ERROR.getCustomException(
                "Service error", "Service error", 500)));

        // When
        CompletionStage<Response<RulesAndFeaturesResponse>> result = configController.getFeatures();

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
          });
          testContext.completeNow();
        });
      });
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetScopeAndSdks {

    @Test
    void shouldGetScopeAndSdks(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        GetScopeAndSdksResponse mockResponse = GetScopeAndSdksResponse.builder()
            .scope(Arrays.asList("logs", "traces", "metrics", "baggage"))
            .sdks(Arrays.asList("android", "ios", "reactNative"))
            .build();

        when(configService.getScopeAndSdks()).thenReturn(Single.just(mockResponse));

        // When
        CompletionStage<Response<GetScopeAndSdksResponse>> result = configController.getScopeAndSdks();

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp.getData());
            assertNotNull(resp.getData().getScope());
            assertNotNull(resp.getData().getSdks());
            assertEquals(4, resp.getData().getScope().size());
            assertEquals(3, resp.getData().getSdks().size());
            verify(configService, times(1)).getScopeAndSdks();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldHandleServiceErrorForScopeAndSdks(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        // Given
        when(configService.getScopeAndSdks())
            .thenReturn(Single.error(ServiceError.DATABASE_ERROR.getCustomException(
                "Service error", "Service error", 500)));

        // When
        CompletionStage<Response<GetScopeAndSdksResponse>> result = configController.getScopeAndSdks();

        // Then
        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
          });
          testContext.completeNow();
        });
      });
    }
  }
}
