package org.dreamhorizon.pulseserver.service.usagelimit.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Usage statistics from ClickHouse for a project.
 * Contains current month's events and sessions usage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStats {
  private String projectId;
  private Long eventsUsed;
  private Long sessionsUsed;
}
