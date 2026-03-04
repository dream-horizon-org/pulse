package org.dreamhorizon.pulseserver.dao.usagelimit.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUsageLimit {
  private Long projectUsageLimitId;
  private String projectId;
  private String usageLimits; // JSON string
  private Boolean isActive;
  private Instant createdAt;
  private Instant disabledAt;
  private String disabledBy;
  private String disabledReason;
  private String createdBy;
}

