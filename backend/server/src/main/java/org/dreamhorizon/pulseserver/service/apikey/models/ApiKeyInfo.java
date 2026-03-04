package org.dreamhorizon.pulseserver.service.apikey.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full API key information including the raw key.
 * Used for internal responses and when returning to the user on creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyInfo {
  private Long apiKeyId;
  private String projectId;
  private String displayName;
  private String rawApiKey; // Only populated on creation or for internal endpoints
  private Boolean isActive;
  private String expiresAt;
  private String gracePeriodEndsAt;
  private String createdBy;
  private String createdAt;
  private String deactivatedAt;
  private String deactivatedBy;
  private String deactivationReason;
}

