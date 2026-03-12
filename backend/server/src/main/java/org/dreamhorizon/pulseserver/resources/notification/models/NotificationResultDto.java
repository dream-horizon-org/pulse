package org.dreamhorizon.pulseserver.resources.notification.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResultDto {
  private String recipient;
  private ChannelType channelType;
  private NotificationStatus status;
  private String externalId;
  private String errorMessage;
}
