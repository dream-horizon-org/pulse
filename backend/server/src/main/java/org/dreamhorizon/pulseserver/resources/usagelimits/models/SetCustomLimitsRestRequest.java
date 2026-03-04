package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.tiers.models.UsageLimitValueRestDto;

import java.util.Map;

/**
 * Request to set custom usage limits for a project.
 * Supports partial updates - only the provided limits will be changed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetCustomLimitsRestRequest {

  @NotEmpty(message = "limits cannot be empty")
  private Map<String, UsageLimitValueRestDto> limits;
}

