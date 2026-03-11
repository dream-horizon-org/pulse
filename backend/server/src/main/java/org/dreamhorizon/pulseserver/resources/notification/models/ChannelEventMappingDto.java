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
public class ChannelEventMappingDto {
  private Long id;
  private String projectId;
  private Long channelId;
  private ChannelType channelType;
  private String channelName;
  private String eventName;
  private String recipient;
  private String recipientName;
  private Boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;
}
