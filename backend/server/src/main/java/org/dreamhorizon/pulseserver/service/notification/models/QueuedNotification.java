package org.dreamhorizon.pulseserver.service.notification.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueuedNotification {
  private NotificationMessage message;
  private String receiptHandle;
  private String messageId;
  private int receiveCount;
}
