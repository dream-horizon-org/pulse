package org.dreamhorizon.pulsealertscron.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class UsageLimitsApiResponse {

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Response {
    private List<ProjectLimit> limits;
    private Integer count;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ProjectLimit {
    private Integer projectUsageLimitId;
    private String projectId;
    private Map<String, LimitMetric> usageLimits;
    private Boolean isActive;
    private String createdAt;
    private String createdBy;
    private String disabledAt;
    private String disabledBy;
    private String disabledReason;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LimitMetric {
    private String displayName;
    private String windowType;
    private String dataType;
    private Integer value;
    private Integer overage;
    private Integer finalThreshold;
  }
}
