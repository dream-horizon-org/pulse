package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.configs.models.SamplingMatchType;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;
import jakarta.validation.constraints.NotNull;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@NotNull
public class SamplingMatchCondition {
  @JsonProperty("type")
  private SamplingMatchType type;

  @JsonProperty("sdks")
  private List<Sdk> sdks;

  @JsonProperty("value")
  private String value;
}
