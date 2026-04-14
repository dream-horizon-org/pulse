package org.dreamhorizon.pulseserver.service.interaction.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SuggestedInteractionEdge {
  private String from;
  private String to;
  @JsonProperty("mean_gap_s")
  private Double meanGapS;
  @JsonProperty("median_gap_s")
  private Double medianGapS;
  private Double cv;
  @JsonProperty("p5_s")
  private Double p5S;
  @JsonProperty("p95_s")
  private Double p95S;
}
