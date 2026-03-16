package org.dreamhorizon.pulseserver.service.usagelimit.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a usage notification that needs to be sent.
 * Contains information about which threshold was crossed for which metric.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageNotification {
  private String projectId;
  private String metricType;  // "sessions" or "events"
  private Integer threshold;   // 50, 75, 90, 100
  private Integer percentage;  // actual percentage used
  private Long currentUsage;
  private Long limit;
}
