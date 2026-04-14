package org.dreamhorizon.pulseserver.service.configs;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.FeatureConfig;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;
import org.dreamhorizon.pulseserver.service.configs.models.ImagePrivacy;
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
      assertThat(config.getSampling().getCriticalSessionPolicies()).isNotNull();
      assertThat(config.getSampling().getSignalsToSample()).isEmpty();
    }

    @Test
    void shouldIncludeSignalsConfig() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      assertThat(config.getSignals()).isNotNull();
      assertThat(config.getSignals().getScheduleDurationMs()).isEqualTo(5000);
      assertThat(config.getSignals().getLogsCollectorUrl()).isNotBlank();
      assertThat(config.getSignals().getMetricCollectorUrl()).isNotBlank();
      assertThat(config.getSignals().getSpanCollectorUrl()).isNotBlank();
      assertThat(config.getSignals().getCustomEventCollectorUrl()).isNotBlank();
      assertThat(config.getSignals().getAttributesToDrop()).isEmpty();
      assertThat(config.getSignals().getAttributesToAdd()).isEmpty();
      assertThat(config.getSignals().getMetricsToAdd()).isEmpty();
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
      assertThat(config.getFeatures()).hasSize(17);

      assertThat(config.getFeatures()).extracting(FeatureConfig::getFeatureName)
          .contains(
              Features.interaction,
              Features.java_crash,
              Features.js_crash,
              Features.java_anr,
              Features.network_change,
              Features.custom_events,
              Features.rn_screen_load,
              Features.rn_screen_interactive,
              Features.rn_screen_session,
              Features.session_replay,
              Features.ios_network,
              Features.rn_network,
              Features.ios_crash,
              Features.ios_lifecycle,
              Features.android_activity,
              Features.android_fragment,
              Features.android_slowrendering
          );
    }

    @Test
    void shouldIncludeSessionReplayFeatureWithExpectedDefaults() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      FeatureConfig sessionReplayFeature = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.session_replay)
          .findFirst()
          .orElse(null);
      assertThat(sessionReplayFeature).isNotNull();
      assertThat(sessionReplayFeature.getSessionSampleRate()).isEqualTo(0.0);
      assertThat(sessionReplayFeature.getSdks())
          .containsExactlyInAnyOrder(
              Sdk.pulse_android_java,
              Sdk.pulse_android_rn,
              Sdk.pulse_ios_swift,
              Sdk.pulse_ios_rn
          );

      assertThat(sessionReplayFeature.getConfig()).isInstanceOf(SessionReplayFeatureConfig.class);
      SessionReplayFeatureConfig replayConfig = (SessionReplayFeatureConfig) sessionReplayFeature.getConfig();
      assertThat(replayConfig.getTextAndInputPrivacy()).isEqualTo(TextAndInputPrivacy.MASK_ALL);
      assertThat(replayConfig.getImagePrivacy()).isEqualTo(ImagePrivacy.MASK_ALL);
      assertThat(replayConfig.getThrottleDelayMs()).isEqualTo(1000L);
      assertThat(replayConfig.getScreenshotScale()).isEqualTo(1.0f);
      assertThat(replayConfig.getScreenshotQuality()).isEqualTo(30);
      assertThat(replayConfig.getFlushIntervalSeconds()).isEqualTo(60);
      assertThat(replayConfig.getFlushAt()).isEqualTo(10);
      assertThat(replayConfig.getMaxBatchSize()).isEqualTo(50);
      assertThat(replayConfig.getReplayApiBaseUrl()).isNotBlank();
    }

    @Test
    void shouldSetDisabledFeaturesWithZeroSampleRate() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      
      FeatureConfig iosLifecycle = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.ios_lifecycle)
          .findFirst()
          .orElse(null);
      assertThat(iosLifecycle).isNotNull();
      assertThat(iosLifecycle.getSessionSampleRate()).isEqualTo(0.0);
      assertThat(iosLifecycle.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_ios_swift, Sdk.pulse_ios_rn);

      FeatureConfig androidFragment = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.android_fragment)
          .findFirst()
          .orElse(null);
      assertThat(androidFragment).isNotNull();
      assertThat(androidFragment.getSessionSampleRate()).isEqualTo(0.0);
      assertThat(androidFragment.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_android_java, Sdk.pulse_android_rn);
    }

    @Test
    void shouldSetAndroidSpecificFeatures() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      
      FeatureConfig javaCrash = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.java_crash)
          .findFirst()
          .orElse(null);
      assertThat(javaCrash).isNotNull();
      assertThat(javaCrash.getSessionSampleRate()).isEqualTo(1.0);
      assertThat(javaCrash.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_android_java, Sdk.pulse_android_rn);

      FeatureConfig jsCrash = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.js_crash)
          .findFirst()
          .orElse(null);
      assertThat(jsCrash).isNotNull();
      assertThat(jsCrash.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_android_java, Sdk.pulse_android_rn);

      FeatureConfig javaAnr = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.java_anr)
          .findFirst()
          .orElse(null);
      assertThat(javaAnr).isNotNull();
      assertThat(javaAnr.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_android_java, Sdk.pulse_android_rn);

      FeatureConfig slowRendering = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.android_slowrendering)
          .findFirst()
          .orElse(null);
      assertThat(slowRendering).isNotNull();
      assertThat(slowRendering.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_android_java, Sdk.pulse_android_rn);

      FeatureConfig activity = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.android_activity)
          .findFirst()
          .orElse(null);
      assertThat(activity).isNotNull();
      assertThat(activity.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_android_java, Sdk.pulse_android_rn);
    }

    @Test
    void shouldSetIosSpecificFeatures() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      
      FeatureConfig iosCrash = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.ios_crash)
          .findFirst()
          .orElse(null);
      assertThat(iosCrash).isNotNull();
      assertThat(iosCrash.getSessionSampleRate()).isEqualTo(1.0);
      assertThat(iosCrash.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_ios_swift, Sdk.pulse_ios_rn);

      FeatureConfig iosNetwork = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.ios_network)
          .findFirst()
          .orElse(null);
      assertThat(iosNetwork).isNotNull();
      assertThat(iosNetwork.getSessionSampleRate()).isEqualTo(1.0);
      assertThat(iosNetwork.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_ios_swift, Sdk.pulse_ios_rn);
    }

    @Test
    void shouldSetReactNativeSpecificFeatures() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      
      FeatureConfig rnScreenLoad = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.rn_screen_load)
          .findFirst()
          .orElse(null);
      assertThat(rnScreenLoad).isNotNull();
      assertThat(rnScreenLoad.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_android_rn, Sdk.pulse_ios_rn);

      FeatureConfig rnScreenInteractive = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.rn_screen_interactive)
          .findFirst()
          .orElse(null);
      assertThat(rnScreenInteractive).isNotNull();
      assertThat(rnScreenInteractive.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_android_rn, Sdk.pulse_ios_rn);

      FeatureConfig rnScreenSession = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.rn_screen_session)
          .findFirst()
          .orElse(null);
      assertThat(rnScreenSession).isNotNull();
      assertThat(rnScreenSession.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_android_rn, Sdk.pulse_ios_rn);

      FeatureConfig rnNetwork = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.rn_network)
          .findFirst()
          .orElse(null);
      assertThat(rnNetwork).isNotNull();
      assertThat(rnNetwork.getSdks()).containsExactlyInAnyOrder(Sdk.pulse_android_rn, Sdk.pulse_ios_rn);
    }

    @Test
    void shouldIncludeAllSdksForEachFeature() {
      ConfigData config = DefaultSdkConfigTemplate.createDefaultConfig("creator");
      
      FeatureConfig interaction = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.interaction)
          .findFirst()
          .orElse(null);
      assertThat(interaction).isNotNull();
      assertThat(interaction.getSdks())
          .containsExactlyInAnyOrder(Sdk.pulse_android_java, Sdk.pulse_android_rn, Sdk.pulse_ios_swift, Sdk.pulse_ios_rn);

      FeatureConfig networkChange = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.network_change)
          .findFirst()
          .orElse(null);
      assertThat(networkChange).isNotNull();
      assertThat(networkChange.getSdks())
          .containsExactlyInAnyOrder(Sdk.pulse_android_java, Sdk.pulse_android_rn, Sdk.pulse_ios_swift, Sdk.pulse_ios_rn);

      FeatureConfig customEvents = config.getFeatures().stream()
          .filter(f -> f.getFeatureName() == Features.custom_events)
          .findFirst()
          .orElse(null);
      assertThat(customEvents).isNotNull();
      assertThat(customEvents.getSdks())
          .containsExactlyInAnyOrder(Sdk.pulse_android_java, Sdk.pulse_android_rn, Sdk.pulse_ios_swift, Sdk.pulse_ios_rn);
    }
  }
}
