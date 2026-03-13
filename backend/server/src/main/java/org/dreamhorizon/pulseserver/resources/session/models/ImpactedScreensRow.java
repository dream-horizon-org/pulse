package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps a single row from the impacted screens query on stack_trace_events.
 * Each field is a delimited string (delimiter: |||) of distinct screen names.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImpactedScreensRow {

    @JsonProperty("SessionId")
    private String sessionId;

    @JsonProperty("crashScreens")
    private String crashScreens;

    @JsonProperty("anrScreens")
    private String anrScreens;

    @JsonProperty("nonFatalScreens")
    private String nonFatalScreens;
}
