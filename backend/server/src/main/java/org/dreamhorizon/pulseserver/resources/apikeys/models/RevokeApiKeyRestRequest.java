package org.dreamhorizon.pulseserver.resources.apikeys.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REST request to revoke an API key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokeApiKeyRestRequest {
  private Integer gracePeriodDays; // Optional, defaults to 0 (immediate)
}

