package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to reset project usage limits to tier defaults.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetLimitsRestRequest {

  @Min(value = 1, message = "tierId must be at least 1")
  private Integer tierId; // Optional, defaults to 1 (free tier)
}

