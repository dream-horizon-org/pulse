package org.dreamhorizon.pulseserver.dao.configs.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.configs.models.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SdkConfigData {

  private SamplingConfig sampling;

  private SignalsConfig signals;

  private InteractionConfig interaction;

  private List<FeatureConfig> features;
}
