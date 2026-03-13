-- =============================================================================
-- Session Summary: Materialized Views for fast session listing
-- =============================================================================
--
-- Without these MVs, the session listing query scans ALL spans (20x per session)
-- and does GROUP BY + sort at query time. With MVs, each session is
-- pre-aggregated to ~1 row, making listing queries 20-100x faster at scale.
--
-- Architecture:
--   Two MVs feed the same AggregatingMergeTree target (session_summary):
--     MV1 (session_summary_mv)    — from otel_traces: timestamps, metadata, metrics
--     MV2 (session_crash_mv)      — from stack_trace_events: crash/ANR/non-fatal counts
--
--   Rows with the same (ProjectId, sessionId) merge via SimpleAggregateFunction:
--     sum() for counts, min()/max() for timestamps, any() for strings.
--
-- qualityScore is stored as sum + count components so we can compute the
-- correct average across partial aggregates: sum(apdexSum) / sum(apdexCount).
-- =============================================================================

-- Step 1: Target table
CREATE TABLE IF NOT EXISTS otel.session_summary
(
    `ProjectId`              LowCardinality(String),
    `sessionId`              String,
    `startTime`              SimpleAggregateFunction(min, DateTime64(9, 'UTC')),
    `endTime`                SimpleAggregateFunction(max, DateTime64(9, 'UTC')),
    `userId`                 SimpleAggregateFunction(any, String),
    `platform`               SimpleAggregateFunction(any, LowCardinality(String)),
    `appVersion`             SimpleAggregateFunction(any, LowCardinality(String)),
    `osVersion`              SimpleAggregateFunction(any, LowCardinality(String)),
    `deviceModel`            SimpleAggregateFunction(any, LowCardinality(String)),
    `networkProvider`        SimpleAggregateFunction(any, LowCardinality(String)),
    `geoCountry`             SimpleAggregateFunction(any, LowCardinality(String)),
    `geoRegion`              SimpleAggregateFunction(any, LowCardinality(String)),

    -- Quality score = apdexSum / apdexCount (computed at query time)
    `apdexSum`               SimpleAggregateFunction(sum, Float64),
    `apdexCount`             SimpleAggregateFunction(sum, UInt64),

    -- Issue counts
    `networkErrors`          SimpleAggregateFunction(sum, UInt64),
    `interactionErrors`      SimpleAggregateFunction(sum, UInt64),
    `crashCount`             SimpleAggregateFunction(sum, UInt64),
    `anrCount`               SimpleAggregateFunction(sum, UInt64),
    `nonFatal`               SimpleAggregateFunction(sum, UInt64),
    `slowInteractionCount`   SimpleAggregateFunction(sum, UInt64),
    `frozenFrameCount`       SimpleAggregateFunction(sum, Float64),
    `spanCount`              SimpleAggregateFunction(sum, UInt64),

    INDEX idx_user_id userId TYPE bloom_filter(0.01) GRANULARITY 1
)
ENGINE = AggregatingMergeTree
ORDER BY (ProjectId, sessionId)
SETTINGS index_granularity = 8192;


-- Step 2a: MV1 — otel_traces → session_summary (timestamps, metadata, metrics)
-- Note: crashCount/anrCount/nonFatal are NOT sourced here — they come from MV2.
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.session_summary_mv
TO otel.session_summary
AS SELECT
    ProjectId,
    SessionId                                                           AS sessionId,
    min(Timestamp)                                                      AS startTime,
    max(Timestamp)                                                      AS endTime,
    any(UserId)                                                         AS userId,
    any(Platform)                                                       AS platform,
    any(AppVersion)                                                     AS appVersion,
    any(OsVersion)                                                      AS osVersion,
    any(DeviceModel)                                                    AS deviceModel,
    any(NetworkProvider)                                                AS networkProvider,
    any(GeoCountry)                                                     AS geoCountry,
    any(GeoState)                                                       AS geoRegion,

    sumIf(
        toFloat64OrZero(SpanAttributes['pulse.interaction.apdex_score']),
        SpanAttributes['pulse.interaction.apdex_score'] != ''
    )                                                                   AS apdexSum,
    countIf(SpanAttributes['pulse.interaction.apdex_score'] != '')      AS apdexCount,

    countIf(StatusCode = 'Error')                                       AS networkErrors,
    countIf(
        ifNull(SpanAttributes['pulse.interaction.is_error'], '') = 'true'
    )                                                                   AS interactionErrors,
    countIf(
        ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor'
    )                                                                   AS slowInteractionCount,
    sum(
        toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count'])
    )                                                                   AS frozenFrameCount,
    count()                                                             AS spanCount

FROM otel.otel_traces
WHERE SessionId != ''
GROUP BY ProjectId, SessionId;


-- Step 2b: MV2 — stack_trace_events → session_summary (crash/ANR/non-fatal counts)
-- Feeds the same target table; rows merge via SimpleAggregateFunction(sum).
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.session_crash_mv
TO otel.session_summary
AS SELECT
    ProjectId,
    SessionId                                                           AS sessionId,
    min(Timestamp)                                                      AS startTime,
    max(Timestamp)                                                      AS endTime,
    countIf(PulseType = 'device.crash')                                 AS crashCount,
    countIf(PulseType = 'device.anr')                                   AS anrCount,
    countIf(PulseType = 'non_fatal')                                    AS nonFatal
FROM otel.stack_trace_events
WHERE SessionId != ''
GROUP BY ProjectId, SessionId;


-- Step 3a: Backfill from otel_traces (metrics + metadata)
INSERT INTO otel.session_summary
SELECT
    ProjectId,
    SessionId                                                           AS sessionId,
    min(Timestamp)                                                      AS startTime,
    max(Timestamp)                                                      AS endTime,
    any(UserId)                                                         AS userId,
    any(Platform)                                                       AS platform,
    any(AppVersion)                                                     AS appVersion,
    any(OsVersion)                                                      AS osVersion,
    any(DeviceModel)                                                    AS deviceModel,
    any(NetworkProvider)                                                AS networkProvider,
    any(GeoCountry)                                                     AS geoCountry,
    any(GeoState)                                                       AS geoRegion,
    sumIf(
        toFloat64OrZero(SpanAttributes['pulse.interaction.apdex_score']),
        SpanAttributes['pulse.interaction.apdex_score'] != ''
    )                                                                   AS apdexSum,
    countIf(SpanAttributes['pulse.interaction.apdex_score'] != '')      AS apdexCount,
    countIf(StatusCode = 'Error')                                       AS networkErrors,
    countIf(
        ifNull(SpanAttributes['pulse.interaction.is_error'], '') = 'true'
    )                                                                   AS interactionErrors,
    countIf(
        ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor'
    )                                                                   AS slowInteractionCount,
    sum(
        toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count'])
    )                                                                   AS frozenFrameCount,
    count()                                                             AS spanCount
FROM otel.otel_traces
WHERE SessionId != ''
GROUP BY ProjectId, SessionId;

-- Step 3b: Backfill from stack_trace_events (crash/ANR/non-fatal counts)
-- Must include startTime/endTime so rows survive WHERE startTime >= ... filters.
INSERT INTO otel.session_summary (ProjectId, sessionId, startTime, endTime, crashCount, anrCount, nonFatal)
SELECT
    ProjectId,
    SessionId                                                           AS sessionId,
    min(Timestamp)                                                      AS startTime,
    max(Timestamp)                                                      AS endTime,
    countIf(PulseType = 'device.crash')                                 AS crashCount,
    countIf(PulseType = 'device.anr')                                   AS anrCount,
    countIf(PulseType = 'non_fatal')                                    AS nonFatal
FROM otel.stack_trace_events
WHERE SessionId != ''
GROUP BY ProjectId, SessionId;


-- =============================================================================
-- Step 4: Session listing query (uses the MV)
-- =============================================================================
-- GROUP BY sessionId handles not-yet-merged partial rows.
-- min/max/sum/any re-aggregate correctly because they match the
-- SimpleAggregateFunction types in the table.
-- =============================================================================

-- Example: list 50 most recent sessions
-- SELECT
--   sessionId,
--   min(startTime)                                                     AS startTime,
--   toUInt64(dateDiff('millisecond', min(startTime), max(endTime)))    AS durationMs,
--   any(userId)                                                        AS user,
--   if(sum(apdexCount) > 0,
--      round(sum(apdexSum) / sum(apdexCount), 2), null)               AS qualityScore,
--   sum(networkErrors)                                                 AS networkErrors,
--   sum(interactionErrors)                                             AS interactionErrors,
--   sum(crashCount)                                                    AS crashCount,
--   sum(anrCount)                                                      AS anrCount,
--   sum(nonFatal)                                                      AS nonFatal,
--   sum(slowInteractionCount)                                          AS slowInteractionCount,
--   sum(frozenFrameCount)                                              AS frozenFrameCount,
--   any(platform)                                                      AS platform
-- FROM otel.session_summary
-- WHERE ProjectId = 'project-123'
--   AND startTime >= now() - INTERVAL 7 DAY
-- GROUP BY sessionId
-- ORDER BY startTime DESC, sessionId DESC
-- LIMIT 51;

-- Example: with quick filters (HAVING)
-- ...same as above...
-- GROUP BY sessionId
-- HAVING sum(crashCount) > 0 OR sum(anrCount) > 0 OR sum(networkErrors) > 0
-- ORDER BY startTime DESC, sessionId DESC
-- LIMIT 51;
