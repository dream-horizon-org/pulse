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

    @JsonProperty("criticalSessionPolicies")
    private CriticalSessionPolicies criticalSessionPolicies;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class DefaultSampling {
    @JsonProperty("sessionSampleRate")
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

    @JsonProperty("sdks")
    private List<Sdk> sdks;

    @JsonProperty("value")
    private String value;

    @JsonProperty("sessionSampleRate")
    private double sessionSampleRate;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class CriticalEventPolicies {
    @JsonProperty("alwaysSend")
    private List<CriticalPolicyRule> alwaysSend;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class CriticalSessionPolicies {
    @JsonProperty("alwaysSend")
    private List<CriticalPolicyRule> alwaysSend;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @NotNull
  public static class CriticalPolicyRule {

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
  public static class SignalsConfig {

    @NotNull
    @JsonProperty("scheduleDurationMs")
    private int scheduleDurationMs;

    @JsonProperty("logsCollectorUrl")
    private String logsCollectorUrl;

    @JsonProperty("metricCollectorUrl")
    private String metricCollectorUrl;

    @JsonProperty("spanCollectorUrl")
    private String spanCollectorUrl;

    @NotNull
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

    @JsonProperty("sessionSampleRate")
    private Double sessionSampleRate;

    @JsonProperty("sdks")
    private List<Sdk> sdks;
  }
}
