package org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row from {@code otel.funnel_results} (latest run query).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelResultRow {

  @JsonProperty("stepIndex")
  private Integer stepIndex;

  @JsonProperty("stepName")
  private String stepName;

  @JsonProperty("userCount")
  private Long userCount;

  @JsonProperty("conversionPct")
  private Double conversionPct;

  @JsonProperty("medianStepSeconds")
  private Long medianStepSeconds;

  @JsonProperty("runTime")
  private Instant runTime;
}
