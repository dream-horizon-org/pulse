package org.dreamhorizon.pulsealertscron.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class ApiKeysResponse {

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Response {
    private List<ApiKey> apiKeys;
    private Integer count;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiKey {
    private Integer apiKeyId;
    private String projectId;
    private String apiKey;
    private Boolean isActive;
    private String expiresAt;
    private String gracePeriodEndsAt;
  }
}
