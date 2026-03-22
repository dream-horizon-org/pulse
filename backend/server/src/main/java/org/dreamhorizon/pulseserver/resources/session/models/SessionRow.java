package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps a single row from the session listing query against {@code otel.session_summary}.
 * <p>
 * <b>Why {@code s_} on JSON property names?</b> The listing SQL selects aggregated columns with
 * explicit aliases (e.g. {@code min(startTime) AS s_startTime}, {@code sum(crashCount) AS s_crashCount}).
 * Those aliases are what ClickHouse returns in the result set. The {@code s_} prefix:
 * <ul>
 *   <li>Matches the query aliases exactly so Jackson can deserialize without a custom row mapper.</li>
 *   <li>Avoids ambiguity with non-aggregated names (e.g. raw {@code startTime} vs aggregated start time).</li>
 *   <li>Keeps {@code sessionId} (the grouped key) distinct from the {@code s_*} measure columns.</li>
 * </ul>
 * Java field names stay readable ({@code startTime}, {@code crashCount}); only the JSON keys use {@code s_*}.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionRow {

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("s_startTime")
    private String startTime;

    @JsonProperty("s_durationMs")
    private Long durationMs;

    @JsonProperty("s_user")
    private String user;

    @JsonProperty("s_qualityScore")
    private Double qualityScore;

    @JsonProperty("s_networkErrors")
    private Long networkErrors;

    @JsonProperty("s_interactionErrors")
    private Long interactionErrors;

    @JsonProperty("s_crashCount")
    private Long crashCount;

    @JsonProperty("s_anrCount")
    private Long anrCount;

    @JsonProperty("s_nonFatal")
    private Long nonFatal;

    @JsonProperty("s_slowInteractionCount")
    private Long slowInteractionCount;

    @JsonProperty("s_frozenFrameCount")
    private Double frozenFrameCount;

    @JsonProperty("s_platform")
    private String platform;

    @JsonProperty("s_spanCount")
    private Long spanCount;
}
