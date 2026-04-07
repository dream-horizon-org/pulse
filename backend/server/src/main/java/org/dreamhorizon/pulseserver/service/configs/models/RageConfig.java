package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Rage-tap detection parameters for the {@code click} SDK feature. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RageConfig {

  @JsonProperty("timeWindowMs")
  private Long timeWindowMs;

  @JsonProperty("threshold")
  private Integer threshold;

  @JsonProperty("radius")
  private Integer radius;
}
