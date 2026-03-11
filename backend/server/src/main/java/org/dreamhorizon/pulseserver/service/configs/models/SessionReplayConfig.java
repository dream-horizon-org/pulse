package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionReplayConfig {

  @JsonProperty("textAndInputPrivacy")
  private TextAndInputPrivacy textAndInputPrivacy;

  @JsonProperty("imagePrivacy")
  private ImagePrivacy imagePrivacy;

  @JsonProperty("throttleDelayMs")
  private Long throttleDelayMs;

  @JsonProperty("screenshotScale")
  private Float screenshotScale;

  @JsonProperty("screenshotQuality")
  private Integer screenshotQuality;

  @JsonProperty("flushIntervalSeconds")
  private Integer flushIntervalSeconds;

  @JsonProperty("flushAt")
  private Integer flushAt;

  @JsonProperty("maxBatchSize")
  private Integer maxBatchSize;

  @JsonProperty("replayApiBaseUrl")
  private String replayApiBaseUrl;
}
