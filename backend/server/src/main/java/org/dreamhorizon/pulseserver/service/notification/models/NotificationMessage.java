package org.dreamhorizon.pulseserver.service.notification.models;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
  private Long logId;
  private String projectId;
  private String idempotencyKey;
  private ChannelType channelType;
  private Long channelId;
  private ChannelConfig channelConfig;
  private Long templateId;
  private TemplateBody templateBody;
  private String recipient;
  private String subject;
  private Map<String, Object> params;
  private Map<String, Object> metadata;
}
