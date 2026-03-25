package org.dreamhorizon.pulseserver.resources.apikeys.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
  private Instant expiresAt;
  private Instant gracePeriodEndsAt;
  private String createdBy;
  private Instant createdAt;
  private Instant deactivatedAt;
  private String deactivatedBy;
  private String deactivationReason;
}

