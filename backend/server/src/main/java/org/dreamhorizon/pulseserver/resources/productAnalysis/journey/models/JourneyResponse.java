package org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.analysis.AnalysisComputedStatus;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelAttributeFilter;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelMode;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JourneyResponse {

  private long id;

  private String projectId;

  private String name;

  private String description;

  private AnalysisComputedStatus status;

  private String anchorEvent;

  private JourneyDirection direction;

  private Integer depth;

  private FunnelMode mode;

  private List<FunnelAttributeFilter> filters;

  private FunnelType journeyType;

  private Instant startTime;

  private Instant endTime;

  private Instant expiry;

  private Integer dateRangeDays;

  private Instant createdAt;

  private Instant updatedAt;

  private String createdBy;

  /**
   * Latest pre-computed path graph from ClickHouse ({@code otel.journey_results}), when available.
   * Same shape as POST {@code /v1/journey/explore} ({@code nodes} + {@code links}).
   */
  private JourneyResultsResponse journeyResults;

  private Instant lastRunAt;

  /** Tags from {@code funnel_journey_tag} for this journey. */
  private List<String> tags;
}
