-- ═══════════════════════════════════════════════════════════════════════════
-- Causal Analysis Materialized Views
-- ═══════════════════════════════════════════════════════════════════════════
--
-- These MVs pre-aggregate raw events into session-level summaries at INSERT
-- time, eliminating full table scans from the causal analysis pipeline.
--
-- Without these MVs, causal queries GROUP BY SessionId over the entire
-- otel_traces/otel_logs tables — at 20TB/day (40B events, 400M sessions),
-- that means 2.4TB of aggregation state per query. With MVs, queries hit
-- pre-computed tables with one row per session (~5-8% storage overhead).
--
-- IMPORTANT: These MVs only process NEW data after creation. To backfill
-- historical data, run the INSERT INTO ... SELECT statements at the bottom.
--
-- Storage impact:
--   ~1 row per session (vs ~100 events per session in base tables)
--   Estimated overhead: 5-8% of base table storage
--
-- Compatible with both standalone MergeTree and ReplicatedMergeTree setups.
-- For the replicated cluster, replace MergeTree with ReplicatedMergeTree
-- and add ON CLUSTER/Distributed wrappers (see bottom of file).
-- ═══════════════════════════════════════════════════════════════════════════


-- ═══════════════════════════════════════════════════════════════════════════
-- MV 1: Session Profiles (replaces get_session_profiles full scan)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Pre-aggregates device context, timestamps, and event counts per session.
-- Original query scanned ALL otel_traces rows, GROUP BY SessionId.
-- Now: one row per (ProjectId, date, SessionId), updated on each INSERT.

CREATE TABLE IF NOT EXISTS otel.causal_session_profiles
(
    `ProjectId`           LowCardinality(String),
    `SessionId`           String,
    `UserId`              AggregateFunction(any, String),
    `DeviceModel`         AggregateFunction(any, LowCardinality(String)),
    `OsVersion`           AggregateFunction(any, LowCardinality(String)),
    `AppVersion`          AggregateFunction(any, LowCardinality(String)),
    `Platform`            AggregateFunction(any, LowCardinality(String)),
    `GeoCountry`          AggregateFunction(any, LowCardinality(String)),
    `NetworkProvider`     AggregateFunction(any, LowCardinality(String)),
    `SessionStart`        SimpleAggregateFunction(min, DateTime64(9, 'UTC')),
    `SessionEnd`          SimpleAggregateFunction(max, DateTime64(9, 'UTC')),
    -- Screen tracking (for reporting only, NOT matching features)
    `ScreenNames`         AggregateFunction(groupUniqArray, String),
    -- Network call counts
    `TotalNetworkCalls`   SimpleAggregateFunction(sum, UInt64),
    `NetErrorCount`       SimpleAggregateFunction(sum, UInt64),
    `NetTimeoutCount`     SimpleAggregateFunction(sum, UInt64)
)
ENGINE = AggregatingMergeTree()
PARTITION BY (ProjectId, toYYYYMMDD(SessionStart))
ORDER BY (ProjectId, SessionId)
SETTINGS index_granularity = 8192;


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.causal_session_profiles_mv
TO otel.causal_session_profiles
AS SELECT
    ProjectId,
    SessionId,
    anyState(UserId)                                                AS UserId,
    anyState(DeviceModel)                                           AS DeviceModel,
    anyState(OsVersion)                                             AS OsVersion,
    anyState(AppVersion)                                            AS AppVersion,
    anyState(Platform)                                              AS Platform,
    anyState(GeoCountry)                                            AS GeoCountry,
    anyState(NetworkProvider)                                       AS NetworkProvider,
    min(Timestamp)                                                  AS SessionStart,
    max(Timestamp)                                                  AS SessionEnd,
    groupUniqArrayState(
        CASE WHEN PulseType IN ('screen_session', 'screen_load')
             THEN SpanAttributes['screen.name']
             ELSE '' END
    )                                                               AS ScreenNames,
    countIf(PulseType LIKE 'network.%')                             AS TotalNetworkCalls,
    countIf(PulseType LIKE 'network.4%' OR PulseType LIKE 'network.5%') AS NetErrorCount,
    countIf(PulseType = 'network.0')                                AS NetTimeoutCount
FROM otel.otel_traces
WHERE SessionId != ''
GROUP BY ProjectId, SessionId;


-- ═══════════════════════════════════════════════════════════════════════════
-- MV 2: Screen Visits (replaces get_screen_visits full scan)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Per-session, per-screen first visit timestamps for journey conditioning.
-- Critical for temporal ordering: "did the conversion happen AFTER the issue?"

CREATE TABLE IF NOT EXISTS otel.causal_screen_visits
(
    `ProjectId`     LowCardinality(String),
    `SessionId`     String,
    `ScreenName`    LowCardinality(String),
    `FirstVisitTs`  SimpleAggregateFunction(min, DateTime64(9, 'UTC'))
)
ENGINE = AggregatingMergeTree()
PARTITION BY (ProjectId, toYYYYMMDD(FirstVisitTs))
ORDER BY (ProjectId, SessionId, ScreenName)
SETTINGS index_granularity = 8192;


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.causal_screen_visits_mv
TO otel.causal_screen_visits
AS SELECT
    ProjectId,
    SessionId,
    SpanAttributes['screen.name'] AS ScreenName,
    min(Timestamp)                AS FirstVisitTs
FROM otel.otel_traces
WHERE SessionId != ''
  AND PulseType IN ('screen_session', 'screen_load')
  AND SpanAttributes['screen.name'] != ''
GROUP BY ProjectId, SessionId, ScreenName;


-- ═══════════════════════════════════════════════════════════════════════════
-- MV 3: Network Operations (replaces discover_conversion_proxies scan)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Pre-extracts operation names from SpanAttributes map at INSERT time.
-- Without this MV, conversion proxy discovery scans ALL network events
-- and extracts Map values on every row — the slowest query at scale.

CREATE TABLE IF NOT EXISTS otel.causal_network_operations
(
    `ProjectId`       LowCardinality(String),
    `Day`             Date,
    `OperationName`   LowCardinality(String),
    `HttpMethod`      LowCardinality(String),
    `PulseType`       LowCardinality(String),
    `TotalCalls`      SimpleAggregateFunction(sum, UInt64),
    `UniqueSessions`  AggregateFunction(uniqCombined64, String)
)
ENGINE = AggregatingMergeTree()
PARTITION BY (ProjectId, toYYYYMM(Day))
ORDER BY (ProjectId, Day, OperationName, HttpMethod)
SETTINGS index_granularity = 8192;


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.causal_network_operations_mv
TO otel.causal_network_operations
AS SELECT
    ProjectId,
    toDate(Timestamp)                                           AS Day,
    SpanAttributes['http.request.header.operation_name']        AS OperationName,
    SpanAttributes['http.method']                               AS HttpMethod,
    PulseType,
    count()                                                     AS TotalCalls,
    uniqCombined64State(SessionId)                              AS UniqueSessions
FROM otel.otel_traces
WHERE SessionId != ''
  AND PulseType LIKE 'network.%'
  AND SpanAttributes['http.request.header.operation_name'] != ''
GROUP BY ProjectId, Day, OperationName, HttpMethod, PulseType;


-- ═══════════════════════════════════════════════════════════════════════════
-- MV 4: Conversion Events (replaces get_conversion_events full scan)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Pre-filters successful network calls with operation names.
-- Stores individual timestamps (not aggregated) for temporal ordering.

CREATE TABLE IF NOT EXISTS otel.causal_conversion_events
(
    `ProjectId`             LowCardinality(String),
    `SessionId`             String,
    `OperationName`         LowCardinality(String),
    `ConversionTimestamp`   DateTime64(9, 'UTC')
)
ENGINE = MergeTree()
PARTITION BY (ProjectId, toYYYYMMDD(ConversionTimestamp))
ORDER BY (ProjectId, OperationName, SessionId, ConversionTimestamp)
SETTINGS index_granularity = 8192;


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.causal_conversion_events_mv
TO otel.causal_conversion_events
AS SELECT
    ProjectId,
    SessionId,
    SpanAttributes['http.request.header.operation_name'] AS OperationName,
    Timestamp                                            AS ConversionTimestamp
FROM otel.otel_traces
WHERE SessionId != ''
  AND PulseType LIKE 'network.2%'
  AND SpanAttributes['http.request.header.operation_name'] != '';


-- ═══════════════════════════════════════════════════════════════════════════
-- MV 5: Log Signals per Session (replaces get_log_signals full scan)
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS otel.causal_log_signals
(
    `ProjectId`           LowCardinality(String),
    `SessionId`           String,
    `JankSlowCount`       SimpleAggregateFunction(sum, UInt64),
    `JankFrozenCount`     SimpleAggregateFunction(sum, UInt64),
    `ClickCount`          SimpleAggregateFunction(sum, UInt64),
    `NetworkChangeCount`  SimpleAggregateFunction(sum, UInt64)
)
ENGINE = SummingMergeTree()
PARTITION BY (ProjectId)
ORDER BY (ProjectId, SessionId)
SETTINGS index_granularity = 8192;


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.causal_log_signals_mv
TO otel.causal_log_signals
AS SELECT
    ProjectId,
    SessionId,
    countIf(PulseType = 'app.jank.slow')   AS JankSlowCount,
    countIf(PulseType = 'app.jank.frozen')  AS JankFrozenCount,
    countIf(PulseType = 'app.click')        AS ClickCount,
    countIf(PulseType = 'network.change')   AS NetworkChangeCount
FROM otel.otel_logs
WHERE SessionId != ''
GROUP BY ProjectId, SessionId;


-- ═══════════════════════════════════════════════════════════════════════════
-- MV 6: Jank Events by Screen (replaces get_jank_events_by_screen scan)
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS otel.causal_jank_by_screen
(
    `ProjectId`      LowCardinality(String),
    `SessionId`      String,
    `PulseType`      LowCardinality(String),
    `ScreenName`     LowCardinality(String),
    `FirstTimestamp`  SimpleAggregateFunction(min, DateTime64(9, 'UTC')),
    `EventCount`     SimpleAggregateFunction(sum, UInt64)
)
ENGINE = AggregatingMergeTree()
PARTITION BY (ProjectId)
ORDER BY (ProjectId, SessionId, PulseType, ScreenName)
SETTINGS index_granularity = 8192;


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.causal_jank_by_screen_mv
TO otel.causal_jank_by_screen
AS SELECT
    ProjectId,
    SessionId,
    PulseType,
    LogAttributes['screen.name'] AS ScreenName,
    min(Timestamp)               AS FirstTimestamp,
    count()                      AS EventCount
FROM otel.otel_logs
WHERE SessionId != ''
  AND PulseType IN ('app.jank.slow', 'app.jank.frozen')
  AND LogAttributes['screen.name'] != ''
GROUP BY ProjectId, SessionId, PulseType, ScreenName;


-- ═══════════════════════════════════════════════════════════════════════════
-- BACKFILL: Run these ONCE after creating MVs to populate from historical data
-- ═══════════════════════════════════════════════════════════════════════════
-- WARNING: These queries scan the full base tables. Run during off-peak hours.
-- For PB-scale tables, add WHERE Timestamp >= now() - INTERVAL N DAY to limit scope.
--
-- INSERT INTO otel.causal_session_profiles
-- SELECT
--     ProjectId, SessionId,
--     anyState(UserId), anyState(DeviceModel), anyState(OsVersion),
--     anyState(AppVersion), anyState(Platform), anyState(GeoCountry),
--     anyState(NetworkProvider), min(Timestamp), max(Timestamp),
--     groupUniqArrayState(CASE WHEN PulseType IN ('screen_session','screen_load')
--         THEN SpanAttributes['screen.name'] ELSE '' END),
--     countIf(PulseType LIKE 'network.%'),
--     countIf(PulseType LIKE 'network.4%' OR PulseType LIKE 'network.5%'),
--     countIf(PulseType = 'network.0')
-- FROM otel.otel_traces WHERE SessionId != ''
-- GROUP BY ProjectId, SessionId;
--
-- INSERT INTO otel.causal_screen_visits
-- SELECT ProjectId, SessionId, SpanAttributes['screen.name'], min(Timestamp)
-- FROM otel.otel_traces
-- WHERE SessionId != '' AND PulseType IN ('screen_session','screen_load')
--   AND SpanAttributes['screen.name'] != ''
-- GROUP BY ProjectId, SessionId, SpanAttributes['screen.name'];
--
-- INSERT INTO otel.causal_conversion_events
-- SELECT ProjectId, SessionId,
--   SpanAttributes['http.request.header.operation_name'], Timestamp
-- FROM otel.otel_traces
-- WHERE SessionId != '' AND PulseType LIKE 'network.2%'
--   AND SpanAttributes['http.request.header.operation_name'] != '';
--
-- INSERT INTO otel.causal_log_signals
-- SELECT ProjectId, SessionId,
--     countIf(PulseType = 'app.jank.slow'),
--     countIf(PulseType = 'app.jank.frozen'),
--     countIf(PulseType = 'app.click'),
--     countIf(PulseType = 'network.change')
-- FROM otel.otel_logs WHERE SessionId != ''
-- GROUP BY ProjectId, SessionId;
--
-- INSERT INTO otel.causal_jank_by_screen
-- SELECT ProjectId, SessionId, PulseType, LogAttributes['screen.name'],
--     min(Timestamp), count()
-- FROM otel.otel_logs
-- WHERE SessionId != '' AND PulseType IN ('app.jank.slow','app.jank.frozen')
--   AND LogAttributes['screen.name'] != ''
-- GROUP BY ProjectId, SessionId, PulseType, LogAttributes['screen.name'];


-- ═══════════════════════════════════════════════════════════════════════════
-- REPLICATED CLUSTER VARIANTS
-- ═══════════════════════════════════════════════════════════════════════════
-- For the replicated setup (pulse-clickhouse cluster), create _local tables
-- with ReplicatedAggregatingMergeTree and Distributed wrappers.
-- Example for causal_session_profiles:
--
-- CREATE TABLE otel.causal_session_profiles_local ON CLUSTER `pulse-clickhouse`
-- (... same columns ...)
-- ENGINE = ReplicatedAggregatingMergeTree(
--     '/clickhouse/tables/{shard}/otel/causal_session_profiles_local', '{replica}')
-- PARTITION BY (ProjectId, toYYYYMMDD(SessionStart))
-- ORDER BY (ProjectId, SessionId);
--
-- CREATE TABLE otel.causal_session_profiles ON CLUSTER `pulse-clickhouse`
-- AS otel.causal_session_profiles_local
-- ENGINE = Distributed(`pulse-clickhouse`, otel, causal_session_profiles_local,
--                       cityHash64(SessionId));
--
-- Note: Distributed sharding key is cityHash64(SessionId) (not TraceId)
-- so all events for a session land on the same shard, enabling local GROUP BY.
