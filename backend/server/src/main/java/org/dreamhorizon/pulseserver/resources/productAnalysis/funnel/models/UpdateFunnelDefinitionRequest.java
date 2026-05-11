package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

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

  @Builder.Default
  private FunnelMode mode = FunnelMode.UNIQUE_USERS;

  @NotNull
  @Builder.Default
  private Integer dateRangeDays = 7;

  private Instant startTime;

  private Instant endTime;

  private Instant expiry;

  /**
   * When set (including empty list), replaces funnel tag mappings; when omitted (JSON absent), tags
   * are left unchanged.
   */
  private List<String> tags;

  /**
   * Optional event-attribute key carrying the numeric order value. Null disables revenue computation.
   */
  private String revenueAttribute;

  /** 0-based revenue-step index. Defaults to the last step when null and {@link #revenueAttribute} is set. */
  private Integer revenueStepIndex;

  /** Display-only ISO-4217 currency code. */
  private String currency;
}
