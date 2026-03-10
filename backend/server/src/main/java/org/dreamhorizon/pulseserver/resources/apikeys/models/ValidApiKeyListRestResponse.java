package org.dreamhorizon.pulseserver.resources.apikeys.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * REST response for listing valid API keys (internal, includes raw keys).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidApiKeyListRestResponse {
  private List<ValidApiKeyRestResponse> apiKeys;
  private Integer count;
}

