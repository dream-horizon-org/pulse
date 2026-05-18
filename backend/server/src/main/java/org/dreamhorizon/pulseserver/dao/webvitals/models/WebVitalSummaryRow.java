package org.dreamhorizon.pulseserver.dao.webvitals.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebVitalSummaryRow {

  @JsonProperty("vital_name")
  private String vitalName;

  private String p75;

  @JsonProperty("good_count")
  private String goodCount;

  @JsonProperty("needs_improvement_count")
  private String needsImprovementCount;

  @JsonProperty("poor_count")
  private String poorCount;

  @JsonProperty("total_count")
  private String totalCount;
}
