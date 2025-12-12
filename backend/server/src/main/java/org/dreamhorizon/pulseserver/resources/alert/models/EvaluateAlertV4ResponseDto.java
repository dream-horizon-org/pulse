package org.dreamhorizon.pulseserver.dto.v1.response.alerts;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateAlertV4ResponseDto {
    @JsonProperty("alert_id")
    private String alertId;

    @JsonProperty("query_id")
    private String queryId;
}


