package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SignalsConfig {
  @JsonProperty("scheduleDurationMs")
  private int scheduleDurationMs;

  @JsonProperty("collectorUrl")
  private String collectorUrl;

  @JsonProperty("attributesToDrop")
  private List<String> attributesToDrop;
}
