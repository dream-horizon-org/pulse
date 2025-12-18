package org.dreamhorizon.pulseserver.resources.alert.models;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateAlertRequestDto {
  @NotNull
  @QueryParam("alertId")
  private Integer alertId;
}


