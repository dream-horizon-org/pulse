package org.dreamhorizon.pulsespark.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FunnelFilter(
        @JsonProperty("field") String field,
        @JsonProperty("operator") String operator,
        @JsonProperty("value") List<String> value
) {
    public FunnelFilter {
        operator = operator != null ? operator : "=";
        value    = value    != null ? List.copyOf(value) : List.of();
    }
}
