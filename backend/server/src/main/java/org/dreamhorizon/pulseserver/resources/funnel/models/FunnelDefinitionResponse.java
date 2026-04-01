package org.dreamhorizon.pulseserver.resources.funnel.models;

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

  /** Derived from funnel_type + latest FUNNEL spark_jobs row (not persisted lifecycle). */
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
}
