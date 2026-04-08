package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.analysis.AnalysisComputedStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelDefinitionResponse {

  private long id;

  private String projectId;

  private String name;

  private String description;

  /**
   * Derived from funnel_type + latest FUNNEL spark_jobs row (not persisted lifecycle).
   */
  private AnalysisComputedStatus status;

  private FunnelType funnelType;

  private StepOrderType stepOrderType;

  private List<FunnelDefinitionStep> steps;

  private List<FunnelAttributeFilter> filters;

  private Long windowSeconds;

  private FunnelMode mode;

  private Integer dateRangeDays;

  private Instant startTime;

  private Instant endTime;

  private Instant expiry;

  private Instant createdAt;

  private Instant updatedAt;

  private String createdBy;

  /**
   * Overall conversion rate (%) from the latest funnel results.
   * Available in listing responses without loading full step-level results.
   */
  private Double overallConversionRate;

  /**
   * Change in overall conversion rate vs the previous run (percentage points).
   * Positive = improvement, negative = regression. 0 when only one run exists.
   */
  private Double conversionTrend;

  /**
   * Latest pre-computed funnel metrics from ClickHouse ({@code otel.funnel_results}), when available.
   * Omitted when ClickHouse query fails or has not produced rows yet.
   */
  private FunnelResultsResponse funnelResults;

  /** Tags from {@code funnel_journey_tag} for this funnel. */
  private List<String> tags;
}
