package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Session-replay-specific config properties on top of the common base.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionReplayFeatureConfig extends FeatureConfigProperties {
    @JsonProperty("maxBatchSize")
    private Integer maxBatchSize;

    // Privacy / PII
    @JsonProperty("textAndInputPrivacy")
    private TextAndInputPrivacy textAndInputPrivacy;

    @JsonProperty("imagePrivacy")
    private ImagePrivacy imagePrivacy;

    // Batching
    @JsonProperty("flushIntervalSeconds")
    private Integer flushIntervalSeconds;

    @JsonProperty("flushAt")
    private Integer flushAt;

  @JsonProperty("throttleDelayMs")
  private Long throttleDelayMs;

  @JsonProperty("screenshotScale")
  private Float screenshotScale;

  @JsonProperty("screenshotQuality")
  private Integer screenshotQuality;

  @JsonProperty("replayApiBaseUrl")
  private String replayApiBaseUrl;
}
