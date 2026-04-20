package org.dreamhorizon.pulseserver.resources.heatmap.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Row for {@code DISTINCT} event names from span {@code Events.Name} with event-level screen filter. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeatmapEventNameRowDto {

  @JsonProperty("event_name")
  private String eventName;
}
