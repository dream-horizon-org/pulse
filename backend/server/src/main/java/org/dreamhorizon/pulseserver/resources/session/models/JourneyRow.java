package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps a single row from the journey query on otel_traces.
 * The journey column is a delimited string (delimiter: |||) because
 * the generic query service stringifies all values via .toString().
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JourneyRow {

    @JsonProperty("SessionId")
    private String sessionId;

    @JsonProperty("journey")
    private String journey;
}
