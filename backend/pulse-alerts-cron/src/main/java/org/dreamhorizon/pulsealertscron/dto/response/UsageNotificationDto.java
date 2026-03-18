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
  private String projectName;
  private Integer threshold;      // 50, 75, 90, 100, or overage limit (e.g. 110)
  private java.util.List<Integer> thresholdsToMark;  // All thresholds to mark (includes lower when we only send highest)
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

  /** Display string for template: "&lt;1" when used>0 but percentage rounds to 0, else numeric value */
  private String eventsPercentageDisplay;
  private String sessionsPercentageDisplay;
}
