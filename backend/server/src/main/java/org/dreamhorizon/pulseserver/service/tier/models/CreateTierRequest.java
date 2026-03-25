package org.dreamhorizon.pulseserver.service.tier.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTierRequest {
  private String name;
  private String displayName;
  private Boolean isCustomLimitsAllowed;
  private Map<String, UsageLimitValue> usageLimitDefaults;
}

