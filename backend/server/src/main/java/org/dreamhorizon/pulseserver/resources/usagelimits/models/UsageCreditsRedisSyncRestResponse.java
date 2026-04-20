package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of internal POST that syncs usage credits to Redis for Kong. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageCreditsRedisSyncRestResponse {
  private int projectsSynced;
  private long durationMs;
}
