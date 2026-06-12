package org.dreamhorizon.pulseserver.service.alert.core.models;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SnoozeAlertRequest {
  @NotNull
  private Integer alertId;

  @NotNull
  private LocalDateTime snoozeFrom;

  @NotNull
  private LocalDateTime snoozeUntil;

  @NotNull
  private String updatedBy;
}
