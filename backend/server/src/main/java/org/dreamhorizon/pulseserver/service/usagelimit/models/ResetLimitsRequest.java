package org.dreamhorizon.pulseserver.service.usagelimit.models;

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
public class ResetLimitsRequest {
  private String projectId;
  private Integer tierId; // Optional, defaults to 1 (free tier)
  private String performedBy;
}

