package org.dreamhorizon.pulseserver.service.usagelimit.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request to set custom usage limits for a project.
 * Supports partial updates - only the provided limits will be changed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetCustomLimitsRequest {
  private String projectId;
  private Map<String, UsageLimitValue> limits; // Partial - only limits to change
  private String performedBy;
}

