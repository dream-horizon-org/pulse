package org.dreamhorizon.pulseserver.resources.apikeys.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * REST response for listing API keys.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyListRestResponse {
  private List<ApiKeyRestResponse> apiKeys;
  private Integer count;
}

