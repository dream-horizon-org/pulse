package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.tiers.models.UsageLimitPublicRestDto;

import java.util.Map;

/**
 * Simplified project usage limit response for public endpoints.
 * Contains only displayName, windowType, and value for each limit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUsageLimitPublicRestResponse {
  private String projectId;
  private Map<String, UsageLimitPublicRestDto> usageLimits;
}

