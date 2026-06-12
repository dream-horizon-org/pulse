package org.dreamhorizon.pulseserver.resources.tiers.models;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageLimitValueRestDto {

  @NotBlank(message = "displayName is required")
  private String displayName;

  @NotBlank(message = "windowType is required")
  private String windowType;

  @NotBlank(message = "dataType is required")
  private String dataType;

  @NotNull(message = "value is required")
  @Min(value = 0, message = "value must be non-negative")
  private Long value;

  @Min(value = 0, message = "overage must be between 0 and 100")
  @Max(value = 100, message = "overage must be between 0 and 100")
  private Integer overage;

  /**
   * Computed field: value with overage applied.
   * finalThreshold = value + (value * overage / 100)
   */
  private Long finalThreshold;
}

