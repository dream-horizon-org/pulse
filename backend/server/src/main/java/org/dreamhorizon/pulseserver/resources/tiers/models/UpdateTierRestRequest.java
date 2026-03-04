package org.dreamhorizon.pulseserver.resources.tiers.models;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTierRestRequest {

  @Size(max = 100, message = "displayName must be less than 100 characters")
  private String displayName;

  private Boolean isCustomLimitsAllowed;

  private Map<String, UsageLimitValueRestDto> usageLimitDefaults;
}

