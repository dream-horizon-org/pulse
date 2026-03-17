package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionListingResponse {

    @JsonProperty("sessions")
    private List<SessionItem> sessions;

    @JsonProperty("page")
    private PageResponse page;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SessionItem {
        @JsonProperty("sessionId")
        private String sessionId;

        @JsonProperty("startTime")
        private String startTime;

        @JsonProperty("durationMs")
        private Long durationMs;

        @JsonProperty("user")
        private String user;

        @JsonProperty("qualityScore")
        private Double qualityScore;

        @JsonProperty("issues")
        private List<IssueItem> issues;

        @JsonProperty("platform")
        private String platform;

        @JsonProperty("spanCount")
        private Long spanCount;

        @JsonProperty("journey")
        private List<String> journey;

        @JsonProperty("impactedScreens")
        private Map<String, List<String>> impactedScreens;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PageResponse {
        @JsonProperty("limit")
        private int limit;

        @JsonProperty("nextCursor")
        private String nextCursor;

        @JsonProperty("hasMore")
        private boolean hasMore;
    }
}
