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
public class NotificationLogDto {
  private Long id;
  private String batchId;
  private ChannelType channelType;
  private String recipient;
  private String subject;
  private String status;
  private Integer attemptCount;
  private String errorMessage;
  private String externalId;
  private Integer latencyMs;
  private Instant createdAt;
  private Instant sentAt;
}
