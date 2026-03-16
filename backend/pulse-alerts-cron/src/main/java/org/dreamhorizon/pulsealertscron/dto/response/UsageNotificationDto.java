package org.dreamhorizon.pulsealertscron.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual usage notification that needs to be sent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageNotificationDto {
  private String projectId;
  private String metricType;  // "sessions" or "events"
  private Integer threshold;   // 50, 75, 90, 100
  private Integer percentage;  // actual percentage used
  private Long currentUsage;
  private Long limit;
}
