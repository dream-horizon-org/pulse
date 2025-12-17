package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.configs.models.FilterMode;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@NotNull
public class FeatureConfig {
  @JsonProperty("featureName")
  private Features featureName;

  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("sessionSampleRate")
  private Double sessionSampleRate;

  @JsonProperty("sdks")
  private List<Sdk> sdks;
}
