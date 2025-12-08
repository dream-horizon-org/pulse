package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.configs.models.FilterMode;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FeatureConfig {
  private String featureName;

  private boolean enabled;

  private Double sessionSampleRate; // nullable (not required in schema)

  private List<Sdk> sdks;
}
