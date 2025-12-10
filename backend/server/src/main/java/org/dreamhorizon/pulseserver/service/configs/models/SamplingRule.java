package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@NotNull
public class SamplingRule {
  @JsonProperty("name")
  private rules name;

  @JsonProperty("match")
  private SamplingMatchCondition match;

  @JsonProperty("session_sample_rate")
  private double sessionSampleRate;
}
