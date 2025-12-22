package org.dreamhorizon.pulseserver.dao.configs.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.configs.models.FeatureConfig;
import org.dreamhorizon.pulseserver.service.configs.models.InteractionConfig;
import org.dreamhorizon.pulseserver.service.configs.models.SamplingConfig;
import org.dreamhorizon.pulseserver.service.configs.models.SignalsConfig;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SdkConfigData {

  private SamplingConfig sampling;

  private SignalsConfig signals;

  private InteractionConfig interaction;

  private List<FeatureConfig> features;
}
