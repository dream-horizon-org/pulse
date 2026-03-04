package org.dreamhorizon.pulseserver.resources.notification.models;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequestDto {

  @NotEmpty(message = "channelTypes is required and cannot be empty")
  private List<ChannelType> channelTypes;

  @NotBlank(message = "eventName is required")
  private String eventName;

  private String idempotencyKey; // Optional - auto-generated if not provided

  @NotNull(message = "recipients is required")
  @Valid
  private RecipientsDto recipients;

  private Map<String, Object> params; // Optional - template parameters

  private Map<String, Object> metadata; // Optional - additional metadata
}
