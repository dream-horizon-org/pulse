package org.dreamhorizon.pulseserver.service.tier.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;

import java.time.Instant;
import java.util.Map;

/**
 * Full tier information including all fields.
 * Used for internal responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierInfo {
  private Integer tierId;
  private String name;
  private String displayName;
  private Boolean isCustomLimitsAllowed;
  private Map<String, UsageLimitValue> usageLimitDefaults;
  private Boolean isActive;
  private Instant createdAt;
}

