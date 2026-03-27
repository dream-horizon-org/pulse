package org.dreamhorizon.pulsealertscron.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from the usage notifications API.
 * Contains list of notifications that need to be sent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageNotificationResponse {
  private List<UsageNotificationDto> notifications;
  private Integer totalProjectsChecked;
  private Integer notificationsDue;
  private String checkedAt;
}
