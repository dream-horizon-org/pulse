package org.dreamhorizon.pulseserver.dao.apikey.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
  private String expiresAt;
  private String gracePeriodEndsAt;
  private String createdBy;
  private String createdAt;
  private String deactivatedAt;
  private String deactivatedBy;
  private String deactivationReason;
}

