package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.tiers.models.UsageLimitValueRestDto;

import java.time.Instant;
import java.util.Map;

/**
 * Full project usage limit response for internal/ endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUsageLimitRestResponse {
  private Long projectUsageLimitId;
  private String projectId;
  private Map<String, UsageLimitValueRestDto> usageLimits;
  private Boolean isActive;
  private Instant createdAt;
  private String createdBy;
  private Instant disabledAt;
  private String disabledBy;
  private String disabledReason;
}

