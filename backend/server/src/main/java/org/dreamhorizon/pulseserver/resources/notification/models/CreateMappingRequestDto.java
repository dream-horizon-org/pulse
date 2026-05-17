package org.dreamhorizon.pulseserver.resources.notification.models;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMappingRequestDto {

  /**
   * Target channel id. Optional when {@link #channelType} resolves to a platform-managed default
   * (e.g. {@link ChannelType#EMAIL} routes to the seeded alerts email channel).
   */
  private Long channelId;

  /**
   * Optional channel type. When set to {@link ChannelType#EMAIL}, the server routes the mapping
   * to the default alerts email channel; callers do not need to pass {@link #channelId}.
   */
  private ChannelType channelType;

  @NotBlank(message = "eventName is required")
  private String eventName;

  private String recipient;
  private String recipientName;
}
