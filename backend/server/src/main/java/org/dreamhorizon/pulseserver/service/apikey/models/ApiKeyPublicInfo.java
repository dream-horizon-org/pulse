package org.dreamhorizon.pulseserver.service.apikey.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * API key information including the raw key.
 * Never includes encrypted key, salt, or digest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyPublicInfo {
  private Long apiKeyId;
  private String projectId;
  private String displayName;
  private String rawApiKey;
  private Boolean isActive;
  private Instant expiresAt;
  private Instant gracePeriodEndsAt;
  private String createdBy;
  private Instant createdAt;
  private Instant deactivatedAt;
  private String deactivatedBy;
  private String deactivationReason;
}

