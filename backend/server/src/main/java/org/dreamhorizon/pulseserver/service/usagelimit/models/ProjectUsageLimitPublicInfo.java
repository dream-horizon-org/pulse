package org.dreamhorizon.pulseserver.service.usagelimit.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Simplified project usage limit information for public API responses.
 * Contains only displayName, windowType, and value for each limit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUsageLimitPublicInfo {
  private String projectId;
  private Map<String, UsageLimitPublicValue> usageLimits;
}

