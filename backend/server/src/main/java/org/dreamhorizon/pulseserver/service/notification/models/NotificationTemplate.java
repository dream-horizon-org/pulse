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
public class NotificationTemplate {
  private Long id;
  private String projectId;
  private String eventName;
  private ChannelType channelType;
  private Integer version;
  private String body;
  private Boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;
}
