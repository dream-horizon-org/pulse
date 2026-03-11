package org.dreamhorizon.pulseserver.resources.tiers.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class CreateTierRestRequest {

  @NotBlank(message = "name is required")
  @Pattern(regexp = "^[a-z][a-z0-9_]{1,48}[a-z0-9]$", message = "name must be 3-50 lowercase alphanumeric characters or underscores, starting with a letter")
  private String name;

  @NotBlank(message = "displayName is required")
  @Size(max = 100, message = "displayName must be less than 100 characters")
  private String displayName;

  @NotNull(message = "isCustomLimitsAllowed is required")
  private Boolean isCustomLimitsAllowed;

  @NotNull(message = "usageLimitDefaults is required")
  private Map<String, UsageLimitValueRestDto> usageLimitDefaults;
}

