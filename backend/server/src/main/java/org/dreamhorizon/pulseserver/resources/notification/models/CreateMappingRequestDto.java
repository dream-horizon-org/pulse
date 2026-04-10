package org.dreamhorizon.pulseserver.resources.notification.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMappingRequestDto {

  @NotNull(message = "channelId is required")
  private Long channelId;

  @NotBlank(message = "eventName is required")
  private String eventName;

  private String recipient;
  private String recipientName;
}
