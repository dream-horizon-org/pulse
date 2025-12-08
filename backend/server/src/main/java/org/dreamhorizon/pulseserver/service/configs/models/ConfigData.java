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
  private FilterConfig filtersConfig;

  private SamplingConfig samplingConfig;

  private SignalsConfig signalsConfig;

  private InteractionConfig interactionConfig;

  private List<FeatureConfig> featureConfigs;

  private String user;
}
