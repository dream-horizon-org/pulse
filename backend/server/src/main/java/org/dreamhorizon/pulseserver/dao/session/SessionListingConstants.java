package org.dreamhorizon.pulseserver.dao.session;

public final class SessionListingConstants {

    private SessionListingConstants() {}

    public static final String LISTING_SELECT = String.join(", ",
            "sessionId",
            "min(startTime) AS s_startTime",
            "toUInt64(dateDiff('millisecond', min(startTime), max(endTime))) AS s_durationMs",
            "anyIf(userId, userId != '') AS s_user",
            "if(sum(apdexCount) > 0, round(sum(apdexSum) / sum(apdexCount), 2), null) AS s_qualityScore",
            "sum(networkErrors) AS s_networkErrors",
            "sum(interactionErrors) AS s_interactionErrors",
            "sum(crashCount) AS s_crashCount",
            "sum(anrCount) AS s_anrCount",
            "sum(nonFatal) AS s_nonFatal",
            "sum(slowInteractionCount) AS s_slowInteractionCount",
            "sum(frozenFrameCount) AS s_frozenFrameCount",
            "anyIf(platform, platform != '') AS s_platform",
            "sum(spanCount) AS s_spanCount"
    );

    public static final String FROM_SESSION_SUMMARY = "FROM otel.session_summary";

    public static final String GROUP_BY_SESSION = "GROUP BY sessionId";

    public static final String JOURNEY_DELIMITER = "|||";

    public static final String JOURNEY_SELECT = String.join("\n",
            "SELECT",
            "  SessionId,",
            "  arrayStringConcat(arrayFilter(",
            "    x -> x != '',",
            "    arrayMap(t -> t.2, arraySort(t -> t.1, groupArray((",
            "      Timestamp,",
            "      coalesce(",
            "        nullIf(trimBoth(SpanAttributes['page.url']), ''),",
            "        nullIf(trimBoth(SpanAttributes['screen.name']), ''),",
            "        SpanName",
            "      )",
            "    ))))",
            "  ), '|||') AS journey",
            "FROM otel.otel_traces"
    );

    public static final String SEMI_JOIN_SELECT = "SELECT DISTINCT SessionId FROM otel.otel_traces";

    public static final String IMPACTED_SCREENS_SELECT = String.join("\n",
            "SELECT",
            "  SessionId,",
            "  arrayStringConcat(arrayFilter(x -> x != '', groupUniqArrayIf(ScreenName, PulseType = 'device.crash')), '|||') AS crashScreens,",
            "  arrayStringConcat(arrayFilter(x -> x != '', groupUniqArrayIf(ScreenName, PulseType = 'device.anr')),   '|||') AS anrScreens,",
            "  arrayStringConcat(arrayFilter(x -> x != '', groupUniqArrayIf(ScreenName, PulseType = 'non_fatal')),    '|||') AS nonFatalScreens",
            "FROM otel.stack_trace_events"
    );
}
