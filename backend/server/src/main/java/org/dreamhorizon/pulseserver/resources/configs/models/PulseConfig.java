package org.dreamhorizon.pulseserver.resources.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.service.configs.models.FilterMode;
import org.dreamhorizon.pulseserver.service.configs.models.SamplingMatchType;
import org.dreamhorizon.pulseserver.service.configs.models.Scope;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;
import org.dreamhorizon.pulseserver.service.configs.models.rules;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PulseConfig {
  @NotNull
  @JsonProperty("description")
  private String description;

  @NotNull
  @JsonProperty("filters")
  private FilterConfig filters;

  @NotNull
  @JsonProperty("sampling")
  private SamplingConfig sampling;

  @NotNull
  @JsonProperty("signals")
  private SignalsConfig signals;

  @NotNull
  @JsonProperty("interaction")
  private InteractionConfig interaction;

  @NotNull
  @JsonProperty("features")
  private List<FeatureConfig> features;


  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class FilterConfig {

    @JsonProperty("mode")
    private FilterMode mode;

    @JsonProperty("whitelist")
    private List<EventFilter> whitelist;

    @JsonProperty("blacklist")
    private List<EventFilter> blacklist;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class EventFilter {

    @JsonProperty("name")
    private String name;

    @JsonProperty("props")
    private List<EventPropMatch> props;

    @JsonProperty("scope")
    private List<Scope> scope;

    @JsonProperty("sdks")
    private List<Sdk> sdks;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class EventPropMatch {

    @JsonProperty("name")
    private String name;

    @JsonProperty("value")
    private String value; // regex string
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class SamplingConfig {

    @JsonProperty("default")
    private DefaultSampling defaultSampling;

    @JsonProperty("rules")
    private List<SamplingRule> rules;

    @JsonProperty("criticalEventPolicies")
    private CriticalEventPolicies criticalEventPolicies;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class DefaultSampling {
    @JsonProperty("session_sample_rate")
    private double sessionSampleRate;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class SamplingRule {

    @JsonProperty("name")
    private rules name;

    @JsonProperty("match")
    private SamplingMatchCondition match;

    @JsonProperty("session_sample_rate")
    private double sessionSampleRate;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class SamplingMatchCondition {

    @JsonProperty("type")
    private SamplingMatchType type;

    @JsonProperty("sdks")
    private List<Sdk> sdks;

    @JsonProperty("value")
    private String value;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class CriticalEventPolicies {

    @JsonProperty("alwaysSend")
    private List<CriticalEventPolicy> alwaysSend;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class CriticalEventPolicy {

    @JsonProperty("name")
    private String name;

    @JsonProperty("props")
    private List<EventPropMatch> props;

    @JsonProperty("scope")
    private List<Scope> scope;

    @JsonProperty("sdks")
    private List<Sdk> sdks;
  }


  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class SignalsConfig {

    @JsonProperty("scheduleDurationMs")
    private int scheduleDurationMs;

    @JsonProperty("collectorUrl")
    private String collectorUrl;

    @JsonProperty("attributesToDrop")
    private List<String> attributesToDrop;
  }


  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class InteractionConfig {

    @JsonProperty("collectorUrl")
    private String collectorUrl;

    @JsonProperty("configUrl")
    private String configUrl;
    @NotNull
    @JsonProperty("beforeInitQueueSize")
    private int beforeInitQueueSize;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class FeatureConfig {

    @JsonProperty("featureName")
    private Features featureName;

    @JsonProperty("enabled")
    private boolean enabled;

    @JsonProperty("session_sample_rate")
    private Double sessionSampleRate;

    @JsonProperty("sdks")
    private List<Sdk> sdks;
  }
}
