package org.dreamhorizon.pulseserver.resources.apikeys.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of internal POST that syncs API keys to Redis for Kong. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyRedisSyncRestResponse {
  private int keysSynced;
  private long durationMs;
  private String redisKey;
}
