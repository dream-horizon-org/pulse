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
public class NotificationLog {
  private Long id;
  private String projectId;
  private String idempotencyKey;
  private ChannelType channelType;
  private Long channelId;
  private Long templateId;
  private String recipient;
  private String subject;
  private NotificationStatus status;
  private Integer attemptCount;
  private Instant lastAttemptAt;
  private String errorMessage;
  private String errorCode;
  private String externalId;
  private String providerResponse;
  private Integer latencyMs;
  private Instant createdAt;
  private Instant sentAt;
}
