package org.dreamhorizon.pulseserver.resources.notification.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChannelRequestDto {

  private String projectId;

  @NotNull(message = "channelType is required")
  private ChannelType channelType;

  @NotBlank(message = "name is required")
  private String name;

  @NotNull(message = "config is required")
  private ChannelConfig config;

  private List<String> eventNames;
}
