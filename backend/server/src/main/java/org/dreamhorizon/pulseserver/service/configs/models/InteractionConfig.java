package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@NotNull
public class InteractionConfig {
  @JsonProperty("collectorUrl")
  private String collectorUrl;

  @JsonProperty("configUrl")
  private String configUrl;

  @JsonProperty("beforeInitQueueSize")
  private int beforeInitQueueSize;
}
