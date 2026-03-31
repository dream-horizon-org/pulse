package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps a single row from the impacted interactions query on otel_traces.
 * impactedInteractionNames is a delimited string (delimiter: |||) of unique interaction names
 * that had error, slow (Poor), or frozen frames.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImpactedInteractionsRow {

    @JsonProperty("SessionId")
    private String sessionId;

    @JsonProperty("impactedInteractionNames")
    private String impactedInteractionNames;
}
