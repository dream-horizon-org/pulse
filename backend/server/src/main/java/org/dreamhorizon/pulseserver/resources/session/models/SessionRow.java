package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps a single row from the session_summary listing query.
 * Field names use s_ prefix to avoid ClickHouse alias/column name collisions
 * in AggregatingMergeTree queries.
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
