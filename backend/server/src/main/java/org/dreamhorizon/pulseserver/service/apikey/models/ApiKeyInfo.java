package org.dreamhorizon.pulseserver.service.apikey.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
  private Instant expiresAt;
  private Instant gracePeriodEndsAt;
  private String createdBy;
  private Instant createdAt;
  private Instant deactivatedAt;
  private String deactivatedBy;
  private String deactivationReason;
}

