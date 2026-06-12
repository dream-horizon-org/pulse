package org.dreamhorizon.pulseserver.service.alert.core.models;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SnoozeAlertResponse {
  private Boolean isSnoozed;
  private LocalDateTime snoozedFrom;
  private LocalDateTime snoozedUntil;
}
