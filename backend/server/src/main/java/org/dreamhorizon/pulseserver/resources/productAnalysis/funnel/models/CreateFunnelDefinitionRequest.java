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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateFunnelDefinitionRequest {

  @NotBlank
  private String name;

  private String description;

  @Builder.Default
  @NotNull
  private FunnelType funnelType = FunnelType.AUTO;

  @Builder.Default
  @NotNull
  private StepOrderType stepOrderType = StepOrderType.ORDERED;

  @NotEmpty
  @Valid
  private List<FunnelDefinitionStep> steps;

  @Valid
  private List<FunnelAttributeFilter> filters;

  @NotNull
  @Builder.Default
  private Long windowSeconds = 86400L;

  @NotNull
  @Builder.Default
  private FunnelMode mode = FunnelMode.UNIQUE_USERS;

  @NotNull
  @Builder.Default
  private Integer dateRangeDays = 7;

  private Instant startTime;

  private Instant endTime;

  private Instant expiry;
}
