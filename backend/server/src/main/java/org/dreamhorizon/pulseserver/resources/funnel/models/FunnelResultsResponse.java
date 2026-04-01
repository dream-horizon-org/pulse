package org.dreamhorizon.pulseserver.resources.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pre-computed funnel metrics from ClickHouse — matches UI {@code FunnelResponse} for the main
 * funnel visualization (steps + KPIs).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelResultsResponse {

  private List<FunnelStepMeasureResult> steps;

  /** Users (or sessions) entering step 0; same as first step {@code count}. */
  private long totalEnteredUsers;

  /** Overall conversion from first to last step (%). */
  private double overallConversionRate;
}
