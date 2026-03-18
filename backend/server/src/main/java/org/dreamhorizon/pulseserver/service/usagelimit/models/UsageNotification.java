package org.dreamhorizon.pulseserver.service.usagelimit.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a usage notification that needs to be sent.
 * Contains information about which threshold was crossed and details for both metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageNotification {
  private String projectId;
  private String projectName;
  private Integer threshold;      // 50, 75, 90, 100, or overage limit (e.g. 110)
  /** All thresholds to mark as notified (includes lower crossed thresholds when we only send highest) */
  private List<Integer> thresholdsToMark;
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
  /** Display string for template: "&lt;1" when used>0 but percentage rounds to 0, else numeric value */
  private String sessionsPercentageDisplay;
}
