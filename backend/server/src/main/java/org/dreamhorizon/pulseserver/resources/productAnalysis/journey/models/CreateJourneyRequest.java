package org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelAttributeFilter;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelMode;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelType;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.AnalysisBasis;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateJourneyRequest {

  @NotBlank
  private String name;

  private String description;

  @NotBlank
  private String anchorEvent;

  @Builder.Default
  @NotNull
  private AnalysisBasis analysisBasis = AnalysisBasis.EVENT;

  @NotNull
  @Builder.Default
  private JourneyDirection direction = JourneyDirection.START;

  @NotNull
  @Min(1)
  @Builder.Default
  private Integer depth = 5;

  @NotNull
  @Builder.Default
  private FunnelMode mode = FunnelMode.UNIQUE_USERS;

  @Valid
  private List<FunnelAttributeFilter> filters;

  @NotNull
  @Builder.Default
  private FunnelType journeyType = FunnelType.AUTO;

  private Instant startTime;

  private Instant endTime;

  private Instant expiry;

  @NotNull
  @Builder.Default
  private Integer dateRangeDays = 7;

  /** Optional; persisted to {@code funnel_journey_tag} after the journey is created. */
  private List<String> tags;
}
