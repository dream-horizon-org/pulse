package org.dreamhorizon.pulseserver.resources.notification.models;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateDto {
  private Long id;
  private String projectId;
  private String eventName;
  private ChannelType channelType;
  private Integer version;
  private Object body;
  private Boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;
}
