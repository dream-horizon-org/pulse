package org.dreamhorizon.pulseserver.service.configs.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.dreamhorizon.pulseserver.dao.configs.ConfigsDao;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import org.dreamhorizon.pulseserver.resources.configs.models.GetScopeAndSdksResponse;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.resources.configs.models.RulesAndFeaturesResponse;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.FeatureConfig;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.service.configs.models.FilterConfig;
import org.dreamhorizon.pulseserver.service.configs.models.FilterMode;
import org.dreamhorizon.pulseserver.service.configs.models.InteractionConfig;
import org.dreamhorizon.pulseserver.service.configs.models.SamplingConfig;
import org.dreamhorizon.pulseserver.service.configs.models.Scope;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;
import org.dreamhorizon.pulseserver.service.configs.models.SignalsConfig;
import org.dreamhorizon.pulseserver.service.configs.models.rules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class ConfigServiceImplTest {

  @Mock
  ConfigsDao configsDao;

  @Mock
  Vertx vertx;

  @Mock
  Context context;

  ConfigServiceImpl configService;

  @BeforeEach
  void setUp() {
    when(vertx.getOrCreateContext()).thenReturn(context);
    // Mock the context.runOnContext to just run the command immediately
    doAnswer(invocation -> {
      io.vertx.core.Handler<Void> handler = invocation.getArgument(0);
      handler.handle(null);
      return null;
    }).when(context).runOnContext(any());
    configService = new ConfigServiceImpl(vertx, configsDao);
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetConfigByVersion {

    @Test
    void shouldGetConfigByVersionSuccessfully() {
      // Given
      long version = 1L;
      Config expectedConfig = Config.builder()
          .version(version)
          .configData(PulseConfig.builder()
              .description("Test Config")
              .build())
          .build();

      when(configsDao.getConfig(version)).thenReturn(Single.just(expectedConfig));

      // When
      Config result = configService.getConfig(version).blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo(version);
      assertThat(result.getConfigData().getDescription()).isEqualTo("Test Config");

      verify(configsDao, times(1)).getConfig(version);
      verifyNoMoreInteractions(configsDao);
    }

    @Test
    void shouldPropagateErrorWhenDaoFails() {
      // Given
      long version = 1L;
      RuntimeException daoError = new RuntimeException("Config not found");

      when(configsDao.getConfig(version)).thenReturn(Single.error(daoError));

      // When
      var testObserver = configService.getConfig(version).test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Config not found"));

      verify(configsDao, times(1)).getConfig(version);
      verifyNoMoreInteractions(configsDao);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetActiveConfig {

    @Test
    void shouldGetActiveConfigSuccessfully() {
      // Given
      Config expectedConfig = Config.builder()
          .version(5L)
          .configData(PulseConfig.builder()
              .description("Active Config")
              .build())
          .build();

      when(configsDao.getConfig()).thenReturn(Single.just(expectedConfig));

      // When
      Config result = configService.getActiveConfig().blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo(5L);
      assertThat(result.getConfigData().getDescription()).isEqualTo("Active Config");

      verify(configsDao, times(1)).getConfig();
    }

    @Test
    void shouldReturnCachedConfigOnSubsequentCalls() {
      // Given
      Config expectedConfig = Config.builder()
          .version(5L)
          .configData(PulseConfig.builder()
              .description("Active Config")
              .build())
          .build();

      when(configsDao.getConfig()).thenReturn(Single.just(expectedConfig));

      // When - first call
      Config result1 = configService.getActiveConfig().blockingGet();
      // Second call - should use cache
      Config result2 = configService.getActiveConfig().blockingGet();

      // Then
      assertThat(result1).isNotNull();
      assertThat(result2).isNotNull();
      assertThat(result1.getVersion()).isEqualTo(result2.getVersion());

      // DAO should only be called once because of caching
      verify(configsDao, times(1)).getConfig();
    }

    @Test
    void shouldPropagateErrorWhenCacheLoadFails() {
      // Given
      RuntimeException daoError = new RuntimeException("Failed to load config");

      when(configsDao.getConfig()).thenReturn(Single.error(daoError));

      // When
      var testObserver = configService.getActiveConfig().test();

      // Then
      testObserver.assertError(Throwable.class);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestCreateConfig {

    @Test
    void shouldCreateConfigSuccessfully() {
      // Given
      ConfigData configData = ConfigData.builder()
          .description("New Config")
          .user("test_user")
          .filters(FilterConfig.builder()
              .mode(FilterMode.blacklist)
              .whitelist(List.of())
              .blacklist(List.of())
              .build())
          .sampling(SamplingConfig.builder().build())
          .signals(SignalsConfig.builder()
              .scheduleDurationMs(5000)
              .collectorUrl("http://collector.example.com")
              .attributesToDrop(List.of())
              .build())
          .interaction(InteractionConfig.builder()
              .collectorUrl("http://interaction.example.com")
              .configUrl("http://config.example.com")
              .beforeInitQueueSize(100)
              .build())
          .features(List.of(
              FeatureConfig.builder()
                  .featureName(Features.java_crash)
                  .enabled(true)
                  .sessionSampleRate(1.0)
                  .sdks(List.of())
                  .build()
          ))
          .build();

      Config createdConfig = Config.builder()
          .version(10L)
          .configData(PulseConfig.builder()
              .description("New Config")
              .build())
          .build();

      when(configsDao.createConfig(configData)).thenReturn(Single.just(createdConfig));

      // When
      Config result = configService.createConfig(configData).blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo(10L);
      assertThat(result.getConfigData().getDescription()).isEqualTo("New Config");

      verify(configsDao, times(1)).createConfig(configData);
      verifyNoMoreInteractions(configsDao);
    }

    @Test
    void shouldInvalidateCacheAfterCreatingConfig() {
      // Given
      Config initialConfig = Config.builder()
          .version(5L)
          .configData(PulseConfig.builder()
              .description("Initial Config")
              .build())
          .build();

      Config newConfig = Config.builder()
          .version(6L)
          .configData(PulseConfig.builder()
              .description("New Config")
              .build())
          .build();

      ConfigData configData = ConfigData.builder()
          .description("New Config")
          .user("test_user")
          .build();

      // First load to populate cache
      when(configsDao.getConfig()).thenReturn(Single.just(initialConfig), Single.just(newConfig));
      when(configsDao.createConfig(any())).thenReturn(Single.just(newConfig));

      // When
      // First call populates cache
      configService.getActiveConfig().blockingGet();
      // Create config should invalidate cache
      configService.createConfig(configData).blockingGet();
      // This should reload from DAO, not cache
      Config result = configService.getActiveConfig().blockingGet();

      // Then
      assertThat(result.getVersion()).isEqualTo(6L);
      // getConfig() should be called twice (initial load + after cache invalidation)
      verify(configsDao, times(2)).getConfig();
    }

    @Test
    void shouldPropagateErrorWhenCreateFails() {
      // Given
      ConfigData configData = ConfigData.builder()
          .description("New Config")
          .user("test_user")
          .build();

      RuntimeException createError = new RuntimeException("Failed to create config");

      when(configsDao.createConfig(configData)).thenReturn(Single.error(createError));

      // When
      var testObserver = configService.createConfig(configData).test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Failed to create config"));

      verify(configsDao, times(1)).createConfig(configData);
      verifyNoMoreInteractions(configsDao);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetAllConfigDetails {

    @Test
    void shouldGetAllConfigDetailsSuccessfully() {
      // Given
      AllConfigdetails expectedDetails = AllConfigdetails.builder()
          .configDetails(List.of(
              AllConfigdetails.Configdetails.builder()
                  .version(1L)
                  .description("Config 1")
                  .createdBy("user1")
                  .createdAt("2024-01-01 00:00:00")
                  .isactive(false)
                  .build(),
              AllConfigdetails.Configdetails.builder()
                  .version(2L)
                  .description("Config 2")
                  .createdBy("user2")
                  .createdAt("2024-01-02 00:00:00")
                  .isactive(true)
                  .build()
          ))
          .build();

      when(configsDao.getAllConfigDetails()).thenReturn(Single.just(expectedDetails));

      // When
      AllConfigdetails result = configService.getAllConfigDetails().blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getConfigDetails()).hasSize(2);
      assertThat(result.getConfigDetails().get(0).getVersion()).isEqualTo(1L);
      assertThat(result.getConfigDetails().get(1).getVersion()).isEqualTo(2L);
      assertThat(result.getConfigDetails().get(1).isIsactive()).isTrue();

      verify(configsDao, times(1)).getAllConfigDetails();
      verifyNoMoreInteractions(configsDao);
    }

    @Test
    void shouldReturnEmptyListWhenNoConfigs() {
      // Given
      AllConfigdetails emptyDetails = AllConfigdetails.builder()
          .configDetails(List.of())
          .build();

      when(configsDao.getAllConfigDetails()).thenReturn(Single.just(emptyDetails));

      // When
      AllConfigdetails result = configService.getAllConfigDetails().blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getConfigDetails()).isEmpty();

      verify(configsDao, times(1)).getAllConfigDetails();
      verifyNoMoreInteractions(configsDao);
    }

    @Test
    void shouldPropagateErrorWhenDaoFails() {
      // Given
      RuntimeException daoError = new RuntimeException("Database error");

      when(configsDao.getAllConfigDetails()).thenReturn(Single.error(daoError));

      // When
      var testObserver = configService.getAllConfigDetails().test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Database error"));

      verify(configsDao, times(1)).getAllConfigDetails();
      verifyNoMoreInteractions(configsDao);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetRulesAndFeatures {

    @Test
    void shouldGetRulesAndFeaturesSuccessfully() {
      // When
      RulesAndFeaturesResponse result = configService.getRulesandFeatures().blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getRules()).isNotNull();
      assertThat(result.getFeatures()).isNotNull();

      // Verify rules contains all enum values
      List<String> expectedRules = rules.getRules();
      assertThat(result.getRules()).containsExactlyInAnyOrderElementsOf(expectedRules);
      assertThat(result.getRules()).contains("os_version", "app_version", "country", "platform", "state", "device", "network");

      // Verify features contains all enum values
      List<String> expectedFeatures = Features.getFeatures();
      assertThat(result.getFeatures()).containsExactlyInAnyOrderElementsOf(expectedFeatures);
      assertThat(result.getFeatures()).contains("interaction", "java_crash", "java_anr", "network_change", "network_instrumentation", "screen_session");
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetScopeAndSdks {

    @Test
    void shouldGetScopeAndSdksSuccessfully() {
      // When
      GetScopeAndSdksResponse result = configService.getScopeAndSdks().blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getScope()).isNotNull();
      assertThat(result.getSdks()).isNotNull();

      // Verify scope contains all enum values
      List<String> expectedScopes = Arrays.stream(Scope.values())
          .map(Enum::name)
          .collect(Collectors.toList());
      assertThat(result.getScope()).containsExactlyInAnyOrderElementsOf(expectedScopes);
      assertThat(result.getScope()).contains("logs", "traces", "metrics", "baggage");

      // Verify sdks contains all enum values
      List<String> expectedSdks = Arrays.stream(Sdk.values())
          .map(Enum::name)
          .collect(Collectors.toList());
      assertThat(result.getSdks()).containsExactlyInAnyOrderElementsOf(expectedSdks);
      assertThat(result.getSdks()).contains("android_java", "android_rn", "ios_native", "ios_rn");
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestConstructor {

    @Test
    void shouldThrowExceptionWhenContextIsNull() {
      // Given
      Vertx mockVertx = mock(Vertx.class);
      ConfigsDao mockConfigsDao = mock(ConfigsDao.class);
      when(mockVertx.getOrCreateContext()).thenReturn(null);

      // When & Then
      try {
        new ConfigServiceImpl(mockVertx, mockConfigsDao);
        org.junit.jupiter.api.Assertions.fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        assertThat(e.getMessage()).contains("ConfigServiceImpl must be created on a Vert.x context thread");
      }
    }
  }
}
