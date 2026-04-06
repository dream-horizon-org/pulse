package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full replacement body for PUT /v1/funnels/{id}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateFunnelDefinitionRequest {

  @NotBlank
  private String name;

  private String description;

  @NotNull
  private FunnelType funnelType;

  @NotNull
  private StepOrderType stepOrderType;

  @NotEmpty
  @Valid
  private List<FunnelDefinitionStep> steps;

  @Valid
  private List<FunnelAttributeFilter> filters;

  @NotNull
  private Long windowSeconds;

  @NotNull
  private FunnelMode mode;

  @NotNull
  private Integer dateRangeDays;

  private Instant startTime;

  private Instant endTime;

  private Instant expiry;

  /**
   * When set (including empty list), replaces funnel tag mappings; when omitted (JSON absent), tags
   * are left unchanged.
   */
  private List<String> tags;
}
