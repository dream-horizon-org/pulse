package org.dreamhorizon.pulseserver.resources.tiers.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Simplified tier response for public endpoints.
 * Contains only displayName, windowType, and value for each limit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierPublicRestResponse {
  private Integer tierId;
  private String name;
  private String displayName;
  private Boolean isCustomLimitsAllowed;
  private Map<String, UsageLimitPublicRestDto> usageLimits;
}

