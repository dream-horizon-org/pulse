package org.dreamhorizon.pulseserver.resources.webvitals;

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
public class VitalSummaryDto {

  private String name;

  private Double p75;

  @JsonProperty("good_pct")
  private Double goodPct;

  @JsonProperty("needs_improvement_pct")
  private Double needsImprovementPct;

  @JsonProperty("poor_pct")
  private Double poorPct;

  @JsonProperty("total_count")
  private Long totalCount;
}
