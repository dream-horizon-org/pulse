package org.dreamhorizon.pulseserver.resources.notification.models;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationChannelDto {
  private Long id;
  private String projectId;
  private ChannelType channelType;
  private String name;
  private ChannelConfig config;
  private Boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;
}
