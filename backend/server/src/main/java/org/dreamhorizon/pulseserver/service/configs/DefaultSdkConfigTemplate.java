package org.dreamhorizon.pulseserver.service.configs;

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
     * @param createdBy User who created the project
     * @return ConfigData with sensible defaults for all SDK features
     */
    public static ConfigData createDefaultConfig(String createdBy) {
        List<Sdk> allSdks = Arrays.asList(
            Sdk.pulse_android_java, 
            Sdk.pulse_android_rn, 
            Sdk.pulse_ios_swift, 
            Sdk.pulse_ios_rn
        );
        
        // Sampling configuration
        SamplingConfig sampling = SamplingConfig.builder()
            .defaultSampling(DefaultSampling.builder()
                .sessionSampleRate(1.0)
                .build())
            .rules(new ArrayList<>())
            .criticalEventPolicies(CriticalEventPolicies.builder()
                .alwaysSend(new ArrayList<>())
                .build())
            .criticalSessionPolicies(CriticalSessionPolicies.builder()
                .alwaysSend(new ArrayList<>())
                .build())
            .build();
        
        // Signals configuration
        SignalsConfig signals = SignalsConfig.builder()
            .filters(FilterConfig.builder()
                .mode(FilterMode.blacklist)
                .values(new ArrayList<>())
                .build())
            .scheduleDurationMs(5000)
            .logsCollectorUrl(System.getenv().getOrDefault("LOGS_COLLECTOR_URL", "http://localhost:4318/v1/logs"))
            .metricCollectorUrl(System.getenv().getOrDefault("METRIC_COLLECTOR_URL", "http://localhost:4318/v1/metrics"))
            .spanCollectorUrl(System.getenv().getOrDefault("SPAN_COLLECTOR_URL", "http://localhost:4318/v1/traces"))
            .customEventCollectorUrl(System.getenv().getOrDefault("CUSTOM_EVENT_COLLECTOR_URL", "http://localhost:4318/v1/events"))
            .attributesToDrop(new ArrayList<>())
            .attributesToAdd(new ArrayList<>())
            .build();
        
        // Interaction configuration
        InteractionConfig interaction = InteractionConfig.builder()
            .collectorUrl(System.getenv().getOrDefault("INTERACTION_COLLECTOR_URL", "http://localhost:4318/v1/traces/v1/interactions"))
            .configUrl(System.getenv().getOrDefault("INTERACTION_CONFIG_URL", "http://localhost:8080/v1/interaction-configs/"))
            .beforeInitQueueSize(100)
            .build();
        
        // Feature configurations - enable all features with full sampling (except network_instrumentation)
        List<FeatureConfig> features = new ArrayList<>();
        features.add(createFeature(Features.interaction, 1.0, allSdks));
        features.add(createFeature(Features.java_crash, 1.0, allSdks));
        features.add(createFeature(Features.js_crash, 1.0, allSdks));
        features.add(createFeature(Features.java_anr, 1.0, allSdks));
        features.add(createFeature(Features.network_change, 1.0, allSdks));
        features.add(createFeature(Features.network_instrumentation, 0.0, allSdks)); // Disabled by default
        features.add(createFeature(Features.screen_session, 1.0, allSdks));
        features.add(createFeature(Features.custom_events, 1.0, allSdks));
        features.add(createFeature(Features.rn_screen_load, 1.0, allSdks));
        features.add(createFeature(Features.rn_screen_interactive, 1.0, allSdks));
        
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
}
