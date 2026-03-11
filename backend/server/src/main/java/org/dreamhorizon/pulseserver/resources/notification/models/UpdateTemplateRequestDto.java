package org.dreamhorizon.pulseserver.resources.notification.models;

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
public class UpdateTemplateRequestDto {
  private String eventName;
  private ChannelType channelType;
  private TemplateBody body;
  private Boolean isActive;
}
