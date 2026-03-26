package org.dreamhorizon.pulseserver.service.usagelimit.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Full project usage limit information including all fields.
 * Used for internal responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUsageLimitInfo {
  private Long projectUsageLimitId;
  private String projectId;
  private Map<String, UsageLimitValue> usageLimits;
  private Boolean isActive;
  private Instant createdAt;
  private String createdBy;
  private Instant disabledAt;
  private String disabledBy;
  private String disabledReason;
}

