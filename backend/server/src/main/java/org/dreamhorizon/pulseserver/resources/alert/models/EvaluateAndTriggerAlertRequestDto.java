package org.dreamhorizon.pulseserver.resources.alert.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluateAndTriggerAlertRequestDto {
    @NotNull
    @QueryParam("alertId")
    public Integer alertId;
}
