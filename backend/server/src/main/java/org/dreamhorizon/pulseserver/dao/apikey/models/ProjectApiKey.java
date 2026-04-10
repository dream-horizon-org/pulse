package org.dreamhorizon.pulseserver.dao.apikey.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectApiKey {
  private Long projectApiKeyId;
  private String projectId;
  private String displayName;
  private String apiKeyEncrypted;
  private String encryptionSalt;
  private String apiKeyDigest;
  private Boolean isActive;
  private Instant expiresAt;
  private Instant gracePeriodEndsAt;
  private String createdBy;
  private Instant createdAt;
  private Instant deactivatedAt;
  private String deactivatedBy;
  private String deactivationReason;
}

