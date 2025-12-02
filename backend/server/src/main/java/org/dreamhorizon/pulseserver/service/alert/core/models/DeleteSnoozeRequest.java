package org.dreamhorizon.pulseserver.service.alert.core.models;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DeleteSnoozeRequest {
  @NotNull
  private Integer alertId;

  @NotNull
  private String updatedBy;
}
