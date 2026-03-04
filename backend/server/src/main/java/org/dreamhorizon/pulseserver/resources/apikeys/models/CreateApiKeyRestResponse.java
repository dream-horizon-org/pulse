package org.dreamhorizon.pulseserver.resources.apikeys.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REST response for creating a new API key.
 * Contains the raw API key (only returned on creation).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiKeyRestResponse {
  private Long apiKeyId;
  private String projectId;
  private String displayName;
  private String apiKey; // The raw API key - only returned on creation!
  private String expiresAt;
  private String createdAt;
}

