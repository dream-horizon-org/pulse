package org.dreamhorizon.pulseserver.resources.notification.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTemplateRequestDto {

  @NotBlank(message = "eventName is required")
  private String eventName;

  private ChannelType channelType;

  @NotNull(message = "body is required")
  private Object body;
}
