package org.dreamhorizon.pulseserver.dao.session;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.resources.session.models.SessionRow;

import java.util.function.Function;

@Getter
@RequiredArgsConstructor
public enum SortField {

    START_TIME("min(startTime)", "startTime", SessionRow::getStartTime, true),
    DURATION("dateDiff('millisecond', min(startTime), max(endTime))", "durationMs", SessionRow::getDurationMs, false),
    QUALITY_SCORE("if(sum(apdexCount) > 0, sum(apdexSum) / sum(apdexCount), null)", "qualityScore", SessionRow::getQualityScore, false),
    NETWORK_ERRORS("sum(networkErrors)", "networkErrors", SessionRow::getNetworkErrors, false),
    CRASHES("sum(crashCount)", "crashes", SessionRow::getCrashCount, false),
    ANRS("sum(anrCount)", "anrs", SessionRow::getAnrCount, false),
    SLOW_INTERACTIONS("sum(slowInteractionCount)", "slowInteractions", SessionRow::getSlowInteractionCount, false),
    SPAN_COUNT("sum(spanCount)", "spanCount", SessionRow::getSpanCount, false);

    private final String expression;
    private final String alias;
    private final Function<SessionRow, Object> cursorValueExtractor;
    /** Whether the cursor value is a timestamp that needs parseDateTime64BestEffort() wrapping. */
    private final boolean timestampSort;
}
