package org.dreamhorizon.pulseserver.resources.apikeys.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REST response for API key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyRestResponse {
  private Long apiKeyId;
  private String projectId;
  private String displayName;
  private String apiKey;
  private Boolean isActive;
  private String expiresAt;
  private String gracePeriodEndsAt;
  private String createdBy;
  private String createdAt;
  private String deactivatedAt;
  private String deactivatedBy;
  private String deactivationReason;
}

