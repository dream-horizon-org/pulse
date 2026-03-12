package org.dreamhorizon.pulseserver.service.tier.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitPublicValue;

import java.util.Map;

/**
 * Simplified tier information for public API responses.
 * Contains only displayName, windowType, and value for each limit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierPublicInfo {
  private Integer tierId;
  private String name;
  private String displayName;
  private Boolean isCustomLimitsAllowed;
  private Map<String, UsageLimitPublicValue> usageLimits;
}

