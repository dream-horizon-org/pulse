package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
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

  /**
   * Users (or sessions) entering step 0; same as first step {@code count}.
   */
  private long totalEnteredUsers;

  /**
   * Overall conversion from first to last step (%).
   */
  private double overallConversionRate;

  /**
   * Total revenue across the funnel (sum of order values from completers). {@code null} when the
   * funnel has no revenue configuration.
   */
  private Double totalRevenue;

  /** Total order count across the funnel. {@code null} when revenue isn't configured. */
  private Long totalOrderCount;

  /** Overall AOV = totalRevenue / totalOrderCount. {@code null} when revenue isn't configured. */
  private Double overallAvgOrderValue;

  /** ISO-4217 currency code copied from the funnel definition; {@code null} when not set. */
  private String currency;

  @JsonIgnore
  private Instant lastRunAt;
}
