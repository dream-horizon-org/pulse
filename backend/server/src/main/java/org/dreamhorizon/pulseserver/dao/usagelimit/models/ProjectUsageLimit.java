package org.dreamhorizon.pulseserver.dao.usagelimit.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUsageLimit {
  private Long projectUsageLimitId;
  private String projectId;
  private String usageLimits; // JSON string
  private Boolean isActive;
  private String createdAt;
  private String disabledAt;
  private String disabledBy;
  private String disabledReason;
  private String createdBy;
}

