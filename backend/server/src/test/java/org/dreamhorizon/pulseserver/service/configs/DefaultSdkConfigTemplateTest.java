package org.dreamhorizon.pulseserver.service.configs;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.FeatureConfig;
import org.dreamhorizon.pulseserver.service.configs.models.FeatureConfigProperties;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.service.configs.models.FilterMode;
import org.dreamhorizon.pulseserver.service.configs.models.ImagePrivacy;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;
import org.dreamhorizon.pulseserver.service.configs.models.SessionReplayFeatureConfig;
import org.dreamhorizon.pulseserver.service.configs.models.TextAndInputPrivacy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefaultSdkConfigTemplateTest {

  @Nested
  class CreateDefaultConfig {

    @Test
    void shouldReturnConfigDataWithDescription() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("admin@example.com");
      assertThat(config.getDescription()).isEqualTo("Default initial configuration");
    }

    @Test
    void shouldSetUserToCreatedBy() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("user-123");
      assertThat(config.getUser()).isEqualTo("user-123");
    }

    @Test
    void shouldIncludeSamplingConfig() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      assertThat(config.getSampling()).isNotNull();
      assertThat(config.getSampling().getDefaultSampling()).isNotNull();
      assertThat(config.getSampling().getDefaultSampling().getSessionSampleRate()).isEqualTo(1.0);
      assertThat(config.getSampling().getRules()).isEmpty();
      assertThat(config.getSampling().getCriticalEventPolicies()).isNotNull();
      assertThat(config.getSampling().getCriticalSessionPolicies()).isNotNull();
    }

    @Test
    void shouldIncludeSignalsConfig() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      assertThat(config.getSignals()).isNotNull();
      assertThat(config.getSignals().getFilters()).isNotNull();
      assertThat(config.getSignals().getFilters().getMode()).isEqualTo(FilterMode.blacklist);
      assertThat(config.getSignals().getFilters().getValues()).isEmpty();
      assertThat(config.getSignals().getScheduleDurationMs()).isEqualTo(5000);
      assertThat(config.getSignals().getLogsCollectorUrl()).isNotBlank();
      assertThat(config.getSignals().getMetricCollectorUrl()).isNotBlank();
      assertThat(config.getSignals().getSpanCollectorUrl()).isNotBlank();
      assertThat(config.getSignals().getCustomEventCollectorUrl()).isNotBlank();
      assertThat(config.getSignals().getAttributesToDrop()).isEmpty();
      assertThat(config.getSignals().getAttributesToAdd()).isEmpty();
    }

    @Test
    void shouldIncludeInteractionConfig() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      assertThat(config.getInteraction()).isNotNull();
      assertThat(config.getInteraction().getCollectorUrl()).isNotBlank();
      assertThat(config.getInteraction().getConfigUrl()).isNotBlank();
      assertThat(config.getInteraction().getBeforeInitQueueSize()).isEqualTo(100);
    }

    @Test
    void shouldIncludeAllExpectedFeatures() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      assertThat(config.getFeatures()).isNotEmpty();

      assertThat(config.getFeatures()).extracting(FeatureConfig::getFeatureName)
          .contains(
              Features.interaction,
              Features.java_crash,
              Features.js_crash,
              Features.java_anr,
              Features.network_change,
              Features.network_instrumentation,
              Features.screen_session,
              Features.custom_events,
              Features.rn_screen_load,
              Features.rn_screen_interactive,
              Features.session_replay
          );
    }

    @Test
    void shouldSetNetworkInstrumentationToZeroSampleRate() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      FeatureConfig networkInstrumentation = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.network_instrumentation)
          .findFirst()
          .orElse(null);
      assertThat(networkInstrumentation).isNotNull();
      assertThat(networkInstrumentation.getSessionSampleRate()).isEqualTo(0.0);
    }

    @Test
    void shouldSetOtherFeaturesToFullSampling() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      config.getFeatures().stream()
          .filter(f -> f.getFeatureName() != Features.network_instrumentation)
          .forEach(f -> assertThat(f.getSessionSampleRate())
              .as("Feature " + f.getFeatureName() + " should have 1.0 sample rate")
              .isEqualTo(1.0));
    }

    @Test
    void shouldIncludeAllSdksForEachFeature() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      config.getFeatures().forEach(feature -> {
        assertThat(feature.getSdks())
            .containsExactlyInAnyOrder(
                Sdk.pulse_android_java,
                Sdk.pulse_android_rn,
                Sdk.pulse_ios_swift,
                Sdk.pulse_ios_rn
            );
      });
    }

    @Test
    void shouldIncludeSessionReplayFeatureWithConfig() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      FeatureConfig sessionReplay = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.session_replay)
          .findFirst()
          .orElse(null);
      assertThat(sessionReplay).isNotNull();
      assertThat(sessionReplay.getSessionSampleRate()).isEqualTo(1.0);

      FeatureConfigProperties rawConfig = sessionReplay.getConfig();
      assertThat(rawConfig).isNotNull();
      assertThat(rawConfig).isInstanceOf(SessionReplayFeatureConfig.class);

      SessionReplayFeatureConfig replayConfig = (SessionReplayFeatureConfig) rawConfig;
      assertThat(replayConfig.getTextAndInputPrivacy()).isEqualTo(TextAndInputPrivacy.MASK_ALL);
      assertThat(replayConfig.getImagePrivacy()).isEqualTo(ImagePrivacy.MASK_ALL);
      assertThat(replayConfig.getThrottleDelayMs()).isEqualTo(1000L);
      assertThat(replayConfig.getScreenshotQuality()).isEqualTo(30);
      assertThat(replayConfig.getFlushIntervalSeconds()).isEqualTo(60);
      assertThat(replayConfig.getFlushAt()).isEqualTo(10);
      assertThat(replayConfig.getMaxBatchSize()).isEqualTo(50);
      assertThat(replayConfig.getReplayApiBaseUrl()).isNotBlank();
    }

    @Test
    void shouldNotHaveConfigForNonSessionReplayFeatures() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      config.getFeatures().stream()
          .filter(f -> f.getFeatureName() != Features.session_replay)
          .forEach(f -> assertThat(f.getConfig())
              .as("Feature " + f.getFeatureName() + " should not have config")
              .isNull());
    }
  }
}
