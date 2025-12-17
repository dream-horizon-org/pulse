package org.dreamhorizon.pulseserver.service.configs.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigData {

  @JsonProperty("description")
  private String description;

  @JsonProperty("sampling")
  private SamplingConfig sampling;

  @JsonProperty("signals")
  private SignalsConfig signals;

  @JsonProperty("interaction")
  private InteractionConfig interaction;

  @JsonProperty("features")
  private List<FeatureConfig> features;

  private String user;
}
