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
  @JsonProperty("featureName")
  private String featureName;

  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("session_sample_rate")
  private Double sessionSampleRate; // nullable (not required in schema)

  @JsonProperty("sdks")
  private List<Sdk> sdks;
}
