package org.dreamhorizon.pulseserver.config;

import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for OpenFGA authorization service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Singleton
public class OpenFgaConfig {

  /**
   * OpenFGA API URL (e.g., "http://localhost:8080").
   */
  private String apiUrl;

  /**
   * OpenFGA Store ID.
   */
  private String storeId;

  /**
   * OpenFGA Authorization Model ID.
   */
  private String authorizationModelId;

  /**
   * Whether OpenFGA authorization is enabled.
   * When disabled, all permission checks return true.
   */
  private boolean enabled;
}

