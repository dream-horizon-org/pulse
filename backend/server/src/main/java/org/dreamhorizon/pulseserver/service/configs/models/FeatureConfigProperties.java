package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base config properties shared across all features.
 * Each feature can extend this to add feature-specific settings.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    visible = true,
    property = "featureName",
    defaultImpl = FeatureConfigProperties.Fallback.class
)
@JsonSubTypes({
    @JsonSubTypes.Type(
        value = SessionReplayFeatureConfig.class,
        name = "session_replay")
})
@Data
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class FeatureConfigProperties {

  /**
   * Concrete type used only as Jackson {@code defaultImpl} for polymorphic {@code config} payloads
   * with no feature-specific properties.
   */
  @Data
  @EqualsAndHashCode(callSuper = true)
  @SuperBuilder
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Fallback extends FeatureConfigProperties {
  }
}
