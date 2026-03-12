package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TimeRangeRequest {

    @JsonProperty("from")
    private String from;

    @JsonProperty("to")
    private String to;
}
