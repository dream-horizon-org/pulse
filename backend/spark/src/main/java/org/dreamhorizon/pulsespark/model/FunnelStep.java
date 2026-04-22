package org.dreamhorizon.pulsespark.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FunnelStep(
        @JsonProperty("eventName") String eventName,
        @JsonProperty("stepFilters") List<FunnelFilter> stepFilters
) {
    public FunnelStep {
        stepFilters = stepFilters != null ? List.copyOf(stepFilters) : List.of();
    }
}
