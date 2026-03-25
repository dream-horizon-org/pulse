package org.dreamhorizon.pulseserver.service.usagelimit.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Result of checking all projects for usage notifications.
 * Contains list of notifications that need to be sent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageNotificationResult {
  private List<UsageNotification> notifications;
  private Integer totalProjectsChecked;
  private Integer notificationsDue;
  private Instant checkedAt;
}
