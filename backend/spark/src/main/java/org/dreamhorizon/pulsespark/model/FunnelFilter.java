package org.dreamhorizon.pulsespark.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A single filter condition from steps_json / filters_json.
 * Shape: { "field": "os_name", "operator": "IN", "value": ["Android", "iOS"] }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FunnelFilter(
        @JsonProperty("field") String field,
        @JsonProperty("operator") String operator,
        @JsonProperty("value") List<String> value
) {
    public FunnelFilter {
        operator = (operator != null) ? operator : "=";
        value    = (value    != null) ? List.copyOf(value) : List.of();
    }
}
