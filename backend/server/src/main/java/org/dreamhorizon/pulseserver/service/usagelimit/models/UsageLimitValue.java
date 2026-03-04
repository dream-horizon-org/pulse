package org.dreamhorizon.pulseserver.service.usagelimit.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single usage limit value with its configuration.
 * Used in tier defaults and project usage limits.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageLimitValue {
  private String displayName;
  private String windowType;
  private String dataType;
  private Long value;
  private Integer overage;
  private Long finalThreshold;
}

