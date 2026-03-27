package org.dreamhorizon.pulseserver.resources.journey.models;

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
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelAttributeFilter;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelMode;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateJourneyRequest {

  @NotBlank private String name;

  private String description;

  @NotBlank private String anchorEvent;

  @NotNull private JourneyDirection direction;

  @NotNull @Min(1) private Integer depth;

  @NotNull private FunnelMode mode;

  @Valid private List<FunnelAttributeFilter> filters;

  @NotNull private FunnelType journeyType;

  private Instant startTime;

  private Instant endTime;

  private Instant expiry;

  @NotNull private Integer dateRangeDays;
}
