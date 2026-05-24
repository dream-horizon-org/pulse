package org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelAttributeFilter;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelMode;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelType;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.AnalysisBasis;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateJourneyRequest {

  @NotBlank
  private String name;

  private String description;

  @NotBlank
  private String anchorEvent;

  @NotNull
  @Builder.Default
  private AnalysisBasis analysisBasis = AnalysisBasis.EVENT;

  @NotNull
  private JourneyDirection direction;

  @NotNull
  @Min(1)
  private Integer depth;

  @NotNull
  @Builder.Default
  private FunnelMode mode = FunnelMode.UNIQUE_USERS;

  @Valid
  private List<FunnelAttributeFilter> filters;

  @NotNull
  private FunnelType journeyType;

  private Instant startTime;

  private Instant endTime;

  private Instant expiry;

  @NotNull
  private Integer dateRangeDays;

  /**
   * When set (including empty list), replaces journey tag mappings; when omitted (JSON absent), tags
   * are left unchanged.
   */
  private List<String> tags;
}
