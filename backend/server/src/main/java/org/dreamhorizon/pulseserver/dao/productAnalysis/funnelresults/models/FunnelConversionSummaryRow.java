package org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight row used by the bulk conversion summary query.
 * Returns one row per funnel: the overall conversion % and the trend vs the previous run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelConversionSummaryRow {

  @JsonProperty("funnelId")
  private Long funnelId;

  @JsonProperty("conversionPct")
  private Double conversionPct;

  /** Difference in conversion % between the latest and previous run (percentage points). */
  @JsonProperty("conversionTrend")
  private Double conversionTrend;
}
