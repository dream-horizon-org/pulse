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

  @JsonProperty("filters")
  private FilterConfig filters;

  @JsonProperty("scheduleDurationMs")
  private int scheduleDurationMs;

  @JsonProperty("logsCollectorUrl")
  private String logsCollectorUrl;

  @JsonProperty("metricCollectorUrl")
  private String metricCollectorUrl;

  @JsonProperty("spanCollectorUrl")
  private String spanCollectorUrl;

  @JsonProperty("attributesToDrop")
  private List<EventFilter> attributesToDrop;
}
