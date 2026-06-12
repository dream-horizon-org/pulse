package org.dreamhorizon.pulseserver.resources.notification.models;

import jakarta.validation.Valid;
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

  private Long mappingId;

  private List<ChannelType> channelTypes;

  private String eventName;

  private String idempotencyKey;

  @Valid
  private RecipientsDto recipients;

  private Map<String, Object> params;

  private Map<String, Object> metadata;
}
