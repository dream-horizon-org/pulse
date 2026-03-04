package org.dreamhorizon.pulseserver.service.notification.models;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationChannel {
  private Long id;
  private String projectId;
  private ChannelType channelType;
  private String name;
  private String config;
  private Boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;
}
