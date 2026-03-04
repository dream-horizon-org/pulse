package org.dreamhorizon.pulseserver.resources.apikeys.models;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REST request to create a new API key for a project.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiKeyRestRequest {
  @NotBlank(message = "Display name is required")
  private String displayName;
  
  private String expiresAt; // ISO 8601 format, optional (null means never expires)
}

