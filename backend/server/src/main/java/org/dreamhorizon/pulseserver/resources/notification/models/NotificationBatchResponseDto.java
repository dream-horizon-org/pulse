package org.dreamhorizon.pulseserver.resources.notification.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationBatchResponseDto {
  private String idempotencyKey;
  private int totalRecipients;
  private int queued;
  private int failed;
  private List<NotificationResultDto> results;
}
