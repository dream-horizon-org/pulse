CREATE DATABASE IF NOT EXISTS otel ON CLUSTER `pulse-clickhouse`;

CREATE TABLE IF NOT EXISTS otel.otel_logs_local
ON CLUSTER `pulse-clickhouse`
(
    `Timestamp` DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    `TraceId` String CODEC(ZSTD(1)),    
    `SpanId` FixedString(16) CODEC(ZSTD(1)),
    `TraceFlags` UInt32 CODEC(ZSTD(1)),
    `SeverityText` LowCardinality(String) CODEC(ZSTD(1)),
    `SeverityNumber` Int32 CODEC(ZSTD(1)),
    `ServiceName` LowCardinality(String) CODEC(ZSTD(1)),
    `Body` String CODEC(ZSTD(1)),
    `ResourceSchemaUrl` String CODEC(ZSTD(1)),
    `ResourceAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ScopeSchemaUrl` String CODEC(ZSTD(1)),
    `ScopeName` String CODEC(ZSTD(1)),
    `ScopeVersion` String CODEC(ZSTD(1)),
    `ScopeAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `LogAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
    `SessionId` String MATERIALIZED ifNull(LogAttributes['session.id'], ''),
    `AppVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['app.build_name'], ''),
    `SDKVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], ''),
    `Platform` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], ''),
    `OsVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], ''),
    `GeoState` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['geo.region.iso_code'], ''),
    `GeoCountry` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['geo.country.iso_code'], ''),
    `DeviceModel` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], ''),
    `NetworkProvider` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['network.carrier.name'], ''),
    `UserId` String MATERIALIZED ifNull(nullIf(LogAttributes['user.id'], ''), ifNull(LogAttributes['app.installation.id'], '')),
    `PulseType` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['pulse.type'], 'otel'),
    `EventName` LowCardinality(String) CODEC(ZSTD(1)),
    `MeteringSessionId` String MATERIALIZED ifNull(LogAttributes['metering.session.id'], ''),

    INDEX idx_trace_id TraceId TYPE bloom_filter(0.001) GRANULARITY 1,
    INDEX idx_project_id ProjectId TYPE bloom_filter(0.01) GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/otel_logs_local', '{replica}')
PARTITION BY (ProjectId, toYYYYMMDD(Timestamp))
ORDER BY (ProjectId, ServiceName, PulseType, EventName, toUnixTimestamp(Timestamp))
TTL toDateTime(Timestamp) + INTERVAL 7 DAY TO VOLUME 'cold'
SETTINGS 
    index_granularity = 8192,
    storage_policy = 'tiered',
    ttl_only_drop_parts = 0;

CREATE TABLE IF NOT EXISTS otel.otel_logs
ON CLUSTER `pulse-clickhouse`
AS otel.otel_logs_local
ENGINE = Distributed(`pulse-clickhouse`, otel, otel_logs_local, cityHash64(TraceId));


CREATE TABLE IF NOT EXISTS otel.otel_traces_local
ON CLUSTER `pulse-clickhouse`
(
    `Timestamp` DateTime64(9, 'UTC') CODEC(Delta(8), ZSTD(1)),
    `TraceId` String CODEC(ZSTD(1)),
    `SpanId` FixedString(16) CODEC(ZSTD(1)),
    `ParentSpanId` FixedString(16) CODEC(ZSTD(1)),
    `TraceState` String CODEC(ZSTD(1)),
    `SpanName` LowCardinality(String) CODEC(ZSTD(1)),
    `SpanKind` LowCardinality(String) CODEC(ZSTD(1)),
    `ServiceName` LowCardinality(String) CODEC(ZSTD(1)),
    `ResourceAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ScopeName` LowCardinality(String),
    `ScopeVersion` LowCardinality(String),
    `SpanAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `Duration` Int64 CODEC(ZSTD(1)),
    `StatusCode` LowCardinality(String) CODEC(ZSTD(1)),
    `StatusMessage` String CODEC(ZSTD(1)),
    `Events.Timestamp` Array(DateTime64(9, 'UTC')) CODEC(ZSTD(1)),
    `Events.Name` Array(LowCardinality(String)) CODEC(ZSTD(1)),
    `Events.Attributes` Array(Map(LowCardinality(String), String)) CODEC(ZSTD(1)),
    `Links.TraceId` Array(String) CODEC(ZSTD(1)),
    `Links.SpanId` Array(String) CODEC(ZSTD(1)),
    `Links.TraceState` Array(String) CODEC(ZSTD(1)),
    `Links.Attributes` Array(Map(LowCardinality(String), String)) CODEC(ZSTD(1)),
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
    `SpanType` LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['pulse.type'], ''),
    `PulseType` LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['pulse.type'], ''),
    `SessionId` String MATERIALIZED ifNull(SpanAttributes['session.id'], ''),
    `AppVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['app.build_name'], ''),
    `SDKVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], ''),
    `Platform` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], ''),
    `OsVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], ''),
    `GeoState` LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['geo.region.iso_code'], ''),
    `GeoCountry` LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['geo.country.iso_code'], ''),
    `DeviceModel` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], ''),
    `NetworkProvider` LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['network.carrier.name'], ''),
    `UserId` String MATERIALIZED ifNull(nullIf(SpanAttributes['user.id'], ''), ifNull(SpanAttributes['app.installation.id'], '')),
    `MeteringSessionId` String MATERIALIZED ifNull(SpanAttributes['metering.session.id'], ''),
    INDEX idx_trace_id TraceId TYPE bloom_filter(0.001) GRANULARITY 1,
    INDEX idx_user_id UserId TYPE bloom_filter(0.001) GRANULARITY 1,
    INDEX idx_project_id ProjectId TYPE bloom_filter(0.01) GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/otel_traces_local', '{replica}')
PARTITION BY (ProjectId, toYYYYMMDD(Timestamp))
ORDER BY (ProjectId, ServiceName, PulseType, SpanName, Timestamp)
TTL toDateTime(Timestamp) + INTERVAL 7 DAY TO VOLUME 'cold'
SETTINGS 
    index_granularity = 8192,
    storage_policy = 'tiered',
    ttl_only_drop_parts = 0;

CREATE TABLE IF NOT EXISTS otel.otel_traces
ON CLUSTER `pulse-clickhouse`
AS otel.otel_traces_local
ENGINE = Distributed(`pulse-clickhouse`, otel, otel_traces_local, cityHash64(TraceId));


CREATE TABLE IF NOT EXISTS otel.stack_trace_events_local
ON CLUSTER `pulse-clickhouse`
(
    -- Core
    `Timestamp`             DateTime64(9, 'UTC')        COMMENT 'event time (ms precision, store UTC)',
    `EventName`             LowCardinality(String),
    `Title`                 String,

    -- Exception details
    `ExceptionStackTrace`    String CODEC(ZSTD(12)),
    `ExceptionStackTraceRaw` String CODEC(ZSTD(12)),
    `ExceptionMessage`      String,
    `ExceptionType`         LowCardinality(String),

    -- App/session context
    `Interactions`          Array(LowCardinality(String)),
    `ScreenName`            LowCardinality(String),
    `UserId`                String,
    `SessionId`             String,

    -- Device/app metadata
    `Platform`              LowCardinality(String),
    `OsVersion`             LowCardinality(String),
    `DeviceModel`           LowCardinality(String),
    `AppVersionCode`        LowCardinality(String),
    `AppVersion`            LowCardinality(String),
    `SdkVersion`            LowCardinality(String),
    `BundleId`              String,

    -- Tracing
    `TraceId`               String,
    `SpanId`                FixedString(16),

    -- Grouping keys
    `GroupId`               String,
    `Signature`             String,
    `Fingerprint`           String,

    `ScopeAttributes`       Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `LogAttributes`         Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ResourceAttributes`    Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
    `PulseType` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['pulse.type'], 'otel'),
    `MeteringSessionId` String MATERIALIZED ifNull(LogAttributes['metering.session.id'], ''),
    INDEX idx_project_id ProjectId TYPE bloom_filter(0.01) GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/stack_trace_events_local', '{replica}')
PARTITION BY (ProjectId, toYYYYMMDD(Timestamp))
ORDER BY (ProjectId, GroupId, ExceptionType, toUnixTimestamp(Timestamp))
TTL toDateTime(Timestamp) + INTERVAL 7 DAY TO VOLUME 'cold'
SETTINGS 
    index_granularity = 8192,
    storage_policy = 'tiered',
    ttl_only_drop_parts = 0;

CREATE TABLE IF NOT EXISTS otel.stack_trace_events
ON CLUSTER `pulse-clickhouse`
AS otel.stack_trace_events_local
ENGINE = Distributed(`pulse-clickhouse`, otel, stack_trace_events_local, cityHash64(TraceId));


CREATE TABLE IF NOT EXISTS otel.project_monthly_usage_local
ON CLUSTER `pulse-clickhouse`
(
    project_id String,
    month Date,
    source LowCardinality(String),
    event_count SimpleAggregateFunction(sum, UInt64),
    session_count AggregateFunction(uniqCombined64, String)
)
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(month)
ORDER BY (project_id, month, source);

-- MV 1: Logs (events + sessions)
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_logs_mv
ON CLUSTER `pulse-clickhouse`
TO otel.project_monthly_usage_local
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(Timestamp) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(SessionId, SessionId != '') AS session_count
FROM otel.otel_logs_local
GROUP BY project_id, month, source;

-- MV 2: Traces (events + sessions)
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_traces_mv
ON CLUSTER `pulse-clickhouse`
TO otel.project_monthly_usage_local
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(Timestamp) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(SessionId, SessionId != '') AS session_count
FROM otel.otel_traces_local
GROUP BY project_id, month, source;

-- ============================================================
-- SESSION-ONLY MVs (1 more table: stack traces)
-- event_count = 0 because we don't count events from these.
-- They only contribute session IDs for deduplication.
-- ============================================================

-- MV 3: Stack trace events (sessions only, no event count)
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_stacktraces_sessions_mv
ON CLUSTER `pulse-clickhouse`
TO otel.project_monthly_usage_local
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(Timestamp) AS month,
    'otel' AS source,
    0 AS event_count,
    uniqCombined64StateIf(SessionId, SessionId != '') AS session_count
FROM otel.stack_trace_events_local
GROUP BY project_id, month, source;

