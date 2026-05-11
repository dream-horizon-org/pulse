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

  @Builder.Default
  private FunnelMode mode = FunnelMode.UNIQUE_USERS;

  @NotNull
  @Builder.Default
  private Integer dateRangeDays = 7;

  private Instant startTime;

  private Instant endTime;

  private Instant expiryDate;

  /**
   * Optional; persisted to {@code funnel_journey_tag} after the funnel is created.
   */
  private List<String> tags;

  /**
   * Optional event-attribute key carrying the numeric order value (e.g. {@code order.value}).
   * When set, the compute paths populate per-step Revenue / AvgOrderValue / LostRevenue.
   */
  private String revenueAttribute;

  /**
   * 0-based index of the step that carries the order/purchase event. Defaults to the last step
   * when {@code null} and {@link #revenueAttribute} is set.
   */
  private Integer revenueStepIndex;

  /**
   * Display-only ISO-4217 currency code (e.g. {@code INR}, {@code USD}).
   */
  private String currency;
}
