package org.dreamhorizon.pulseserver.resources.tiers.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Full tier response for  endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierRestResponse {
  private Integer tierId;
  private String name;
  private String displayName;
  private Boolean isCustomLimitsAllowed;
  private Map<String, UsageLimitValueRestDto> usageLimitDefaults;
  private Boolean isActive;
  private Instant createdAt;
}

