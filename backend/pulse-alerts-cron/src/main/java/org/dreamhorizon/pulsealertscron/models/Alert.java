package org.dreamhorizon.pulsealertscron.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Alert {
    @JsonProperty("alert_id")
    private Integer alertId;

    @JsonProperty("evaluation_interval")
    private Integer evaluationInterval;

    private String url;
}
