package org.dreamhorizon.pulseserver.resources.notification.models;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.TemplateBody;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateDto {
  private Long id;
  private String eventName;
  private ChannelType channelType;
  private Integer version;
  private TemplateBody body;
  private Boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;
}
