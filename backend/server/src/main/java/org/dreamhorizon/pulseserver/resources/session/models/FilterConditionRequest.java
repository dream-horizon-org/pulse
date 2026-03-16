package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FilterConditionRequest {

    @JsonProperty("field")
    private String field;

    @JsonProperty("operator")
    private String operator;

    @JsonProperty("value")
    private Object value;
}
