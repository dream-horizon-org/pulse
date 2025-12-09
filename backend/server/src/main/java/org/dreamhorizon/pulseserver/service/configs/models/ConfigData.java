package org.dreamhorizon.pulseserver.service.configs.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigData {
  private String description;

  private FilterConfig filters;

  private SamplingConfig sampling;

  private SignalsConfig signals;

  private InteractionConfig interaction;

  private List<FeatureConfig> features;

  private String user;
}
