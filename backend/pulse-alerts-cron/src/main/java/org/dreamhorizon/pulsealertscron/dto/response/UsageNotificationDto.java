package org.dreamhorizon.pulsealertscron.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual usage notification that needs to be sent.
 * Contains details for both sessions and events metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageNotificationDto {
  private String projectId;
  private Integer threshold;      // 50, 75, 90, 100
  private String notifyFor;       // "sessions" or "events" - which metric triggered notification
  private String templateName;    // Template to use: USAGE_LIMIT_THRESHOLD, USAGE_LIMIT_REACHED, or USAGE_LIMIT_BLOCKED
  
  // Sessions details (always included, percentages capped for display)
  private Long sessionsUsed;
  private Long sessionsLimit;
  private Integer sessionsPercentage;
  private Integer sessionsOverage;
  private Boolean sessionsBlocked;
  private Boolean sessionsAtLimit;
  
  // Events details (always included, percentages capped for display)
  private Long eventsUsed;
  private Long eventsLimit;
  private Integer eventsPercentage;
  private Integer eventsOverage;
  private Boolean eventsBlocked;
  private Boolean eventsAtLimit;
}
