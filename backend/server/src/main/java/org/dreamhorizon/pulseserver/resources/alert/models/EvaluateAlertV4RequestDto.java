package org.dreamhorizon.pulseserver.dto.v1.request.alerts;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateAlertV4RequestDto {
    @NotNull
    @QueryParam("alertId")
    private Integer alertId;
}


