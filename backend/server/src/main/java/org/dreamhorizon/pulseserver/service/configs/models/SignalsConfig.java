package org.dreamhorizon.pulseserver.service.configs.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@NotNull
public class SignalsConfig {
  @JsonProperty("scheduleDurationMs")
  private int scheduleDurationMs;

  @JsonProperty("collectorUrl")
  private String collectorUrl;

  @JsonProperty("attributesToDrop")
  private List<String> attributesToDrop;
}
