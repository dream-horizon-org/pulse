package org.dreamhorizon.pulseserver.resources.configs.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.configs.models.FilterMode;
import org.dreamhorizon.pulseserver.service.configs.models.SamplingMatchType;
import org.dreamhorizon.pulseserver.service.configs.models.Scope;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PulseConfig {
  @JsonProperty("filtersConfig")
  @JsonAlias("filterConfig")
  private FilterConfig filtersConfig;

  @JsonProperty("samplingConfig")
  private SamplingConfig samplingConfig;

  @JsonProperty("signalsConfig")
  private SignalsConfig signalsConfig;

  @JsonProperty("interaction")
  @JsonAlias("interactionConfig")
  private InteractionConfig interactionConfig;

  @JsonProperty("featureConfigs")
  private List<FeatureConfig> featureConfigs;


  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
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
  public static class DefaultSampling {
    @JsonProperty("session_sample_rate")
    private double sessionSampleRate;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SamplingRule {

    @JsonProperty("name")
    private String name;

    @JsonProperty("match")
    private SamplingMatchCondition match;

    @JsonProperty("session_sample_rate")
    private double sessionSampleRate;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
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
  public static class CriticalEventPolicies {

    @JsonProperty("alwaysSend")
    private List<CriticalEventPolicy> alwaysSend;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class CriticalEventPolicy {

    @JsonProperty("name")
    private String name;

    @JsonProperty("props")
    private List<EventPropMatch> props;

    @JsonProperty("scope")
    private List<Scope> scope;
  }


  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
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

    @JsonProperty("beforeInitQueueSize")
    private int beforeInitQueueSize;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class FeatureConfig {

    @JsonProperty("featureName")
    private String featureName;

    @JsonProperty("enabled")
    private boolean enabled;

    @JsonProperty("session_sample_rate")
    private Double sessionSampleRate;

    @JsonProperty("sdks")
    private List<Sdk> sdks;
  }
}
