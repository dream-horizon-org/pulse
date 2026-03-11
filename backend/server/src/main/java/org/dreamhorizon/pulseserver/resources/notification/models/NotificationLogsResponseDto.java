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
public class NotificationLogsResponseDto {
  private List<NotificationLogDto> logs;
}
