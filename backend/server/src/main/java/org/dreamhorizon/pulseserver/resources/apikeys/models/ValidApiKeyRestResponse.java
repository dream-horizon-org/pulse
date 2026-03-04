package org.dreamhorizon.pulseserver.resources.apikeys.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REST response for valid API key (internal, includes raw key).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidApiKeyRestResponse {
  private Long apiKeyId;
  private String projectId;
  private String apiKey; // Raw API key for cron to sync to Redis
  private Boolean isActive;
  private String expiresAt;
  private String gracePeriodEndsAt;
}

