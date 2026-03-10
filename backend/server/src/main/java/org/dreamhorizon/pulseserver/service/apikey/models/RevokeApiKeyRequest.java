package org.dreamhorizon.pulseserver.service.apikey.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to revoke an API key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokeApiKeyRequest {
  private String projectId;
  private Long apiKeyId;
  private Integer gracePeriodDays; // Optional, defaults to 0 (immediate)
  private String revokedBy;
}

