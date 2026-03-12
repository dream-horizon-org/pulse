package org.dreamhorizon.pulseserver.resources.notification.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelConfig;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateChannelRequestDto {
  private String name;
  private ChannelConfig config;
  private Boolean isActive;
}
