package org.dreamhorizon.pulseserver.service.configs;

import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.service.configs.models.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Default SDK configuration template.
 * This configuration is automatically created for each new project.
 */
public class DefaultSdkConfigTemplate {

    /**
     * Creates a default SDK configuration for a new project.
     *
     * @param projectId the project this config belongs to — used to build the per-project interaction.configUrl
     * @param createdBy User who created the project
     * @param appConfig application configuration — provides collector/config base URLs
     * @return ConfigData with sensible defaults for all SDK features
     */
    public static ConfigData createDefaultConfig(String projectId, String createdBy, ApplicationConfig appConfig) {
        List<Sdk> allSdks = Arrays.asList(
            Sdk.pulse_android_java,
            Sdk.pulse_android_rn,
            Sdk.pulse_ios_swift,
            Sdk.pulse_ios_rn,
            Sdk.pulse_web_js
        );
        List<Sdk> iosSdk = Arrays.asList(
            Sdk.pulse_ios_swift,
            Sdk.pulse_ios_rn
        );
        List<Sdk> androidSdk = Arrays.asList(
            Sdk.pulse_android_java,
            Sdk.pulse_android_rn
        );
        List<Sdk> rnSdk = Arrays.asList(
            Sdk.pulse_android_rn,
            Sdk.pulse_ios_rn
        );
        List<Sdk> webJsSdk = Arrays.asList(Sdk.pulse_web_js);

        // Sampling configuration
        SamplingConfig sampling = SamplingConfig.builder()
            .defaultSampling(DefaultSampling.builder()
                .sessionSampleRate(1.0)
                .build())
            .rules(new ArrayList<>())
            .criticalSessionPolicies(CriticalSessionPolicies.builder()
                .alwaysSend(new ArrayList<>())
                .build())
            .signalsToSample(new ArrayList<>())
            .build();

        // Signals configuration — matches applySignalsConfigDefaults in ConfigController
        SignalsConfig signals = SignalsConfig.builder()
            .scheduleDurationMs(5000)
            .logsCollectorUrl(appConfig.getLogsCollectorUrl())
            .metricCollectorUrl(appConfig.getMetricCollectorUrl())
            .spanCollectorUrl(appConfig.getSpanCollectorUrl())
            .customEventCollectorUrl(appConfig.getCustomEventCollectorUrl())
            .attributesToDrop(new ArrayList<>())
            .attributesToAdd(new ArrayList<>())
            .metricsToAdd(new ArrayList<>())
            .build();

        // Interaction configuration — configUrl is project-scoped (matches applyInteractionConfigDefaults in ConfigController)
        InteractionConfig interaction = InteractionConfig.builder()
            .collectorUrl(appConfig.getOtelCollectorUrl())
            .configUrl(appConfig.buildInteractionConfigFileUrl(projectId))
            .beforeInitQueueSize(100)
            .build();

        // Feature configurations - enable all features with full sampling
        List<FeatureConfig> features = new ArrayList<>();
        features.add(createFeature(Features.interaction, 1.0, allSdks));
        features.add(createFeature(Features.java_crash, 1.0, androidSdk));
        // js_crash is shared by RN (Hermes) and Web; separate rows so defaults and future sample rates stay explicit per surface.
        features.add(createFeature(Features.js_crash, 1.0, rnSdk));
        features.add(createFeature(Features.js_crash, 1.0, webJsSdk));
        features.add(createFeature(Features.web_vitals, 1.0, webJsSdk));
        features.add(createFeature(Features.java_anr, 1.0, androidSdk));
        features.add(createFeature(Features.network_change, 1.0, allSdks));
        features.add(createFeature(Features.custom_events, 1.0, allSdks));
        features.add(createFeature(Features.rn_screen_load, 1.0, rnSdk));
        features.add(createFeature(Features.rn_screen_interactive, 1.0, rnSdk));
        features.add(createFeature(Features.rn_screen_session, 1.0, rnSdk));
        // Legacy key for backward compatibility with old RN SDK versions
        features.add(createFeature(Features.screen_session, 1.0, rnSdk));
        features.add(createSessionReplayFeature(0.0, allSdks, appConfig));
        features.add(createClickFeature(0.0, allSdks));
        features.add(createFeature(Features.heatmap, 1.0, allSdks));
        features.add(createFeature(Features.ios_crash, 1.0, iosSdk));
        features.add(createFeature(Features.android_slowrendering, 1.0, androidSdk));
        features.add(createFeature(Features.ios_network, 1.0, iosSdk));
        features.add(createFeature(Features.rn_network, 1.0, rnSdk));
        // Legacy key for backward compatibility with old SDK versions
        features.add(createFeature(Features.network_instrumentation, 1.0, allSdks));
        features.add(createFeature(Features.ios_lifecycle, 0.0, iosSdk));
        features.add(createFeature(Features.android_activity, 1.0, androidSdk));
        features.add(createFeature(Features.android_fragment, 0.0, androidSdk));

        // Create ConfigData
        return ConfigData.builder()
            .description("Default initial configuration")
            .sampling(sampling)
            .signals(signals)
            .interaction(interaction)
            .features(features)
            .user(createdBy)
            .build();
    }

    private static FeatureConfig createFeature(Features name, Double sampleRate, List<Sdk> sdks) {
        return FeatureConfig.builder()
            .featureName(name)
            .sessionSampleRate(sampleRate)
            .sdks(sdks)
            .build();
    }

    private static FeatureConfig createSessionReplayFeature(
        Double sampleRate, List<Sdk> sdks, ApplicationConfig appConfig) {
        SessionReplayFeatureConfig config = SessionReplayFeatureConfig.builder()
            .textAndInputPrivacy(TextAndInputPrivacy.MASK_ALL)
            .imagePrivacy(ImagePrivacy.MASK_ALL)
            .throttleDelayMs(1000L)
            .screenshotScale(1.0f)
            .screenshotQuality(30)
            .flushIntervalSeconds(60)
            .flushAt(10)
            .maxBatchSize(50)
            .replayApiBaseUrl(appConfig.getReplayApiBaseUrl())
            .build();

        return FeatureConfig.builder()
            .featureName(Features.session_replay)
            .sessionSampleRate(sampleRate)
            .sdks(sdks)
            .config((FeatureConfigProperties) config)
            .build();
    }

    private static FeatureConfig createClickFeature(Double sampleRate, List<Sdk> sdks) {
        ClickFeatureConfig config = ClickFeatureConfig.builder()
            .captureContext(true)
            .rage(RageConfig.builder()
                .timeWindowMs(2000L)
                .threshold(3)
                .radius(50)
                .build())
            .build();

        return FeatureConfig.builder()
            .featureName(Features.click)
            .sessionSampleRate(sampleRate)
            .sdks(sdks)
            .config((FeatureConfigProperties) config)
            .build();
    }
}
