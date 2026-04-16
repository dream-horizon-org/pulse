-- Updated ClickHouse Schema for Project-Based Isolation
-- CRITICAL CHANGE: All tables now use ProjectId instead of TenantId
-- This enables per-project data segregation with row-level policies

CREATE TABLE IF NOT EXISTS otel.otel_traces
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
    -- CHANGED: TenantId replaced with ProjectId
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
    `SpanType` LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['pulse.type'], ''), // DEPRECATED: Use PulseType instead
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
    `MeteringSessionId` String MATERIALIZED ifNull(SpanAttributes['pulse.metering.session.id'], ''),
    `UserId` String MATERIALIZED ifNull(nullIf(SpanAttributes['user.id'], ''), ifNull(SpanAttributes['app.installation.id'], '')),
    INDEX idx_session_id SessionId TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_trace_id TraceId TYPE bloom_filter(0.001) GRANULARITY 1
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(Timestamp)
-- CHANGED: ORDER BY now starts with ProjectId instead of TenantId
ORDER BY (ProjectId, ServiceName, PulseType, SpanName, Timestamp)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.otel_logs
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
    `SessionId` String MATERIALIZED ifNull(LogAttributes['session.id'], ''),
    `MeteringSessionId` String MATERIALIZED ifNull(LogAttributes['pulse.metering.session.id'], ''),
    -- CHANGED: TenantId replaced with ProjectId
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
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
    `ScreenName` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['screen.name'], ''),
    `ClickType` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['click.type'], ''),
    `Rage` Bool MATERIALIZED (LogAttributes['click.is_rage'] = 'true'),
    `RageCount` UInt8 MATERIALIZED toUInt8OrZero(LogAttributes['click.rage_count']),
    `XPer` Float32 MATERIALIZED toFloat32OrZero(LogAttributes['app.screen.coordinate.x']),
    `YPer` Float32 MATERIALIZED toFloat32OrZero(LogAttributes['app.screen.coordinate.y']),
    `NormXPer` Float32 MATERIALIZED toFloat32OrZero(LogAttributes['app.screen.coordinate.nx']),
    `NormYPer` Float32 MATERIALIZED toFloat32OrZero(LogAttributes['app.screen.coordinate.ny']),
    `ViewportWidth` UInt16 MATERIALIZED toUInt16OrZero(LogAttributes['device.screen.width']),
    `ViewportHeight` UInt16 MATERIALIZED toUInt16OrZero(LogAttributes['device.screen.height']),
    `AspectRatio` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['device.screen.aspect_ratio'], ''),
    INDEX idx_trace_id TraceId TYPE bloom_filter(0.001) GRANULARITY 1
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(Timestamp)
-- CHANGED: ORDER BY now starts with ProjectId instead of TenantId
ORDER BY (ProjectId, ServiceName, PulseType, EventName, SeverityText, toUnixTimestamp(Timestamp), TraceId)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.otel_metrics_gauge
(
    `ResourceAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ResourceSchemaUrl` String CODEC(ZSTD(1)),
    `ScopeName` String CODEC(ZSTD(1)),
    `ScopeVersion` String CODEC(ZSTD(1)),
    `ScopeAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ScopeDroppedAttrCount` UInt32 CODEC(ZSTD(1)),
    `ScopeSchemaUrl` String CODEC(ZSTD(1)),
    `ServiceName` LowCardinality(String) CODEC(ZSTD(1)),
    `MetricName` String CODEC(ZSTD(1)),
    `MetricDescription` String CODEC(ZSTD(1)),
    `MetricUnit` String CODEC(ZSTD(1)),
    `Attributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `StartTimeUnix` DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    `TimeUnix` DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    `Value` Float64 CODEC(ZSTD(1)),
    `Flags` UInt32 CODEC(ZSTD(1)),
    -- CHANGED: TenantId replaced with ProjectId
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
    `SessionId` String MATERIALIZED ifNull(Attributes['session.id'], ''),
    `MeteringSessionId` String MATERIALIZED ifNull(Attributes['pulse.metering.session.id'], ''),
    `AppVersion` LowCardinality(String) MATERIALIZED ifNull(Attributes['app.build_name'], ''),
    `SDKVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], ''),
    `Platform` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], ''),
    `OsVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], ''),
    `GeoState` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.region.iso_code'], ''),
    `GeoCountry` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.country.iso_code'], ''),
    `DeviceModel` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], ''),
    `NetworkProvider` LowCardinality(String) MATERIALIZED ifNull(Attributes['network.carrier.name'], ''),
    `UserId` String MATERIALIZED ifNull(nullIf(Attributes['user.id'], ''), ifNull(Attributes['app.installation.id'], '')),
    `Exemplars.FilteredAttributes` Array(Map(LowCardinality(String), String)) CODEC(ZSTD(1)),
    `Exemplars.TimeUnix` Array(DateTime64(9)) CODEC(ZSTD(1)),
    `Exemplars.Value` Array(Float64) CODEC(ZSTD(1)),
    `Exemplars.SpanId` Array(String) CODEC(ZSTD(1)),
    `Exemplars.TraceId` Array(String) CODEC(ZSTD(1))
)
ENGINE = MergeTree
PARTITION BY toDate(TimeUnix)
-- CHANGED: ORDER BY now starts with ProjectId instead of TenantId
ORDER BY (ProjectId, ServiceName, MetricName, Attributes, toUnixTimestamp64Nano(TimeUnix))
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.otel_metrics_sum
(
    `ResourceAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ResourceSchemaUrl` String CODEC(ZSTD(1)),
    `ScopeName` String CODEC(ZSTD(1)),
    `ScopeVersion` String CODEC(ZSTD(1)),
    `ScopeAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ScopeDroppedAttrCount` UInt32 CODEC(ZSTD(1)),
    `ScopeSchemaUrl` String CODEC(ZSTD(1)),
    `ServiceName` LowCardinality(String) CODEC(ZSTD(1)),
    `MetricName` String CODEC(ZSTD(1)),
    `MetricDescription` String CODEC(ZSTD(1)),
    `MetricUnit` String CODEC(ZSTD(1)),
    `Attributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `StartTimeUnix` DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    `TimeUnix` DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    `Value` Float64 CODEC(ZSTD(1)),
    `Flags` UInt32 CODEC(ZSTD(1)),
    `Exemplars.FilteredAttributes` Array(Map(LowCardinality(String), String)) CODEC(ZSTD(1)),
    `Exemplars.TimeUnix` Array(DateTime64(9)) CODEC(ZSTD(1)),
    `Exemplars.Value` Array(Float64) CODEC(ZSTD(1)),
    `Exemplars.SpanId` Array(String) CODEC(ZSTD(1)),
    `Exemplars.TraceId` Array(String) CODEC(ZSTD(1)),
    `AggregationTemporality` Int32 CODEC(ZSTD(1)),
    `IsMonotonic` Bool CODEC(ZSTD(1)),
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
    `SessionId` String MATERIALIZED ifNull(Attributes['session.id'], ''),
    `MeteringSessionId` String MATERIALIZED ifNull(Attributes['pulse.metering.session.id'], ''),
    `AppVersion` LowCardinality(String) MATERIALIZED ifNull(Attributes['app.build_name'], ''),
    `SDKVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], ''),
    `Platform` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], ''),
    `OsVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], ''),
    `GeoState` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.region.iso_code'], ''),
    `GeoCountry` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.country.iso_code'], ''),
    `DeviceModel` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], ''),
    `NetworkProvider` LowCardinality(String) MATERIALIZED ifNull(Attributes['network.carrier.name'], ''),
    `UserId` String MATERIALIZED ifNull(nullIf(Attributes['user.id'], ''), ifNull(Attributes['app.installation.id'], ''))
)
ENGINE = MergeTree
PARTITION BY toDate(TimeUnix)
ORDER BY (ProjectId, ServiceName, MetricName, Attributes, toUnixTimestamp64Nano(TimeUnix))
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.otel_metrics_summary
(
    `ResourceAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ResourceSchemaUrl` String CODEC(ZSTD(1)),
    `ScopeName` String CODEC(ZSTD(1)),
    `ScopeVersion` String CODEC(ZSTD(1)),
    `ScopeAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ScopeDroppedAttrCount` UInt32 CODEC(ZSTD(1)),
    `ScopeSchemaUrl` String CODEC(ZSTD(1)),
    `ServiceName` LowCardinality(String) CODEC(ZSTD(1)),
    `MetricName` String CODEC(ZSTD(1)),
    `MetricDescription` String CODEC(ZSTD(1)),
    `MetricUnit` String CODEC(ZSTD(1)),
    `Attributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `StartTimeUnix` DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    `TimeUnix` DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    `Count` UInt64 CODEC(Delta(8), ZSTD(1)),
    `Sum` Float64 CODEC(ZSTD(1)),
    `ValueAtQuantiles.Quantile` Array(Float64) CODEC(ZSTD(1)),
    `ValueAtQuantiles.Value` Array(Float64) CODEC(ZSTD(1)),
    `Flags` UInt32 CODEC(ZSTD(1)),
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
    `SessionId` String MATERIALIZED ifNull(Attributes['session.id'], ''),
    `MeteringSessionId` String MATERIALIZED ifNull(Attributes['pulse.metering.session.id'], ''),
    `AppVersion` LowCardinality(String) MATERIALIZED ifNull(Attributes['app.build_name'], ''),
    `SDKVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], ''),
    `Platform` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], ''),
    `OsVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], ''),
    `GeoState` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.region.iso_code'], ''),
    `GeoCountry` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.country.iso_code'], ''),
    `DeviceModel` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], ''),
    `NetworkProvider` LowCardinality(String) MATERIALIZED ifNull(Attributes['network.carrier.name'], ''),
    `UserId` String MATERIALIZED ifNull(nullIf(Attributes['user.id'], ''), ifNull(Attributes['app.installation.id'], ''))
)
ENGINE = MergeTree
PARTITION BY toDate(TimeUnix)
ORDER BY (ProjectId, ServiceName, MetricName, Attributes, toUnixTimestamp64Nano(TimeUnix))
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.otel_metrics_histogram
(
    `ResourceAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ResourceSchemaUrl` String CODEC(ZSTD(1)),
    `ScopeName` String CODEC(ZSTD(1)),
    `ScopeVersion` String CODEC(ZSTD(1)),
    `ScopeAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ScopeDroppedAttrCount` UInt32 CODEC(ZSTD(1)),
    `ScopeSchemaUrl` String CODEC(ZSTD(1)),
    `ServiceName` LowCardinality(String) CODEC(ZSTD(1)),
    `MetricName` String CODEC(ZSTD(1)),
    `MetricDescription` String CODEC(ZSTD(1)),
    `MetricUnit` String CODEC(ZSTD(1)),
    `Attributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `StartTimeUnix` DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    `TimeUnix` DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    `Count` UInt64 CODEC(Delta(8), ZSTD(1)),
    `Sum` Float64 CODEC(ZSTD(1)),
    `BucketCounts` Array(UInt64) CODEC(ZSTD(1)),
    `ExplicitBounds` Array(Float64) CODEC(ZSTD(1)),
    `Exemplars.FilteredAttributes` Array(Map(LowCardinality(String), String)) CODEC(ZSTD(1)),
    `Exemplars.TimeUnix` Array(DateTime64(9)) CODEC(ZSTD(1)),
    `Exemplars.Value` Array(Float64) CODEC(ZSTD(1)),
    `Exemplars.SpanId` Array(String) CODEC(ZSTD(1)),
    `Exemplars.TraceId` Array(String) CODEC(ZSTD(1)),
    `Flags` UInt32 CODEC(ZSTD(1)),
    `Min` Float64 CODEC(ZSTD(1)),
    `Max` Float64 CODEC(ZSTD(1)),
    `AggregationTemporality` Int32 CODEC(ZSTD(1)),
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
    `SessionId` String MATERIALIZED ifNull(Attributes['session.id'], ''),
    `MeteringSessionId` String MATERIALIZED ifNull(Attributes['pulse.metering.session.id'], ''),
    `AppVersion` LowCardinality(String) MATERIALIZED ifNull(Attributes['app.build_name'], ''),
    `SDKVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], ''),
    `Platform` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], ''),
    `OsVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], ''),
    `GeoState` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.region.iso_code'], ''),
    `GeoCountry` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.country.iso_code'], ''),
    `DeviceModel` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], ''),
    `NetworkProvider` LowCardinality(String) MATERIALIZED ifNull(Attributes['network.carrier.name'], ''),
    `UserId` String MATERIALIZED ifNull(nullIf(Attributes['user.id'], ''), ifNull(Attributes['app.installation.id'], ''))
)
ENGINE = MergeTree
PARTITION BY toDate(TimeUnix)
ORDER BY (ProjectId, ServiceName, MetricName, Attributes, toUnixTimestamp64Nano(TimeUnix))
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.otel_metrics_exp_histogram
(
    `ResourceAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ResourceSchemaUrl` String CODEC(ZSTD(1)),
    `ScopeName` String CODEC(ZSTD(1)),
    `ScopeVersion` String CODEC(ZSTD(1)),
    `ScopeAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ScopeDroppedAttrCount` UInt32 CODEC(ZSTD(1)),
    `ScopeSchemaUrl` String CODEC(ZSTD(1)),
    `ServiceName` LowCardinality(String) CODEC(ZSTD(1)),
    `MetricName` String CODEC(ZSTD(1)),
    `MetricDescription` String CODEC(ZSTD(1)),
    `MetricUnit` String CODEC(ZSTD(1)),
    `Attributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `StartTimeUnix` DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    `TimeUnix` DateTime64(9) CODEC(Delta(8), ZSTD(1)),
    `Count` UInt64 CODEC(Delta(8), ZSTD(1)),
    `Sum` Float64 CODEC(ZSTD(1)),
    `Scale` Int32 CODEC(ZSTD(1)),
    `ZeroCount` UInt64 CODEC(ZSTD(1)),
    `PositiveOffset` Int32 CODEC(ZSTD(1)),
    `PositiveBucketCounts` Array(UInt64) CODEC(ZSTD(1)),
    `NegativeOffset` Int32 CODEC(ZSTD(1)),
    `NegativeBucketCounts` Array(UInt64) CODEC(ZSTD(1)),
    `Exemplars.FilteredAttributes` Array(Map(LowCardinality(String), String)) CODEC(ZSTD(1)),
    `Exemplars.TimeUnix` Array(DateTime64(9)) CODEC(ZSTD(1)),
    `Exemplars.Value` Array(Float64) CODEC(ZSTD(1)),
    `Exemplars.SpanId` Array(String) CODEC(ZSTD(1)),
    `Exemplars.TraceId` Array(String) CODEC(ZSTD(1)),
    `Flags` UInt32 CODEC(ZSTD(1)),
    `Min` Float64 CODEC(ZSTD(1)),
    `Max` Float64 CODEC(ZSTD(1)),
    `AggregationTemporality` Int32 CODEC(ZSTD(1)),
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
    `SessionId` String MATERIALIZED ifNull(Attributes['session.id'], ''),
    `MeteringSessionId` String MATERIALIZED ifNull(Attributes['pulse.metering.session.id'], ''),
    `AppVersion` LowCardinality(String) MATERIALIZED ifNull(Attributes['app.build_name'], ''),
    `SDKVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], ''),
    `Platform` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], ''),
    `OsVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], ''),
    `GeoState` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.region.iso_code'], ''),
    `GeoCountry` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.country.iso_code'], ''),
    `DeviceModel` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], ''),
    `NetworkProvider` LowCardinality(String) MATERIALIZED ifNull(Attributes['network.carrier.name'], ''),
    `UserId` String MATERIALIZED ifNull(nullIf(Attributes['user.id'], ''), ifNull(Attributes['app.installation.id'], ''))
)
ENGINE = MergeTree
PARTITION BY toDate(TimeUnix)
ORDER BY (ProjectId, ServiceName, MetricName, Attributes, toUnixTimestamp64Nano(TimeUnix))
SETTINGS index_granularity = 8192;

-- Unified read surface for performance queries (METRICS dataType); underlying tables receive collector INSERTs
CREATE VIEW IF NOT EXISTS otel.otel_metrics
(
    `Timestamp` DateTime64(9),
    `ServiceName` LowCardinality(String),
    `MetricName` String,
    `Value` Float64,
    `Count` Nullable(UInt64),
    `Sum` Nullable(Float64),
    `Attributes` Map(LowCardinality(String), String),
    `ResourceAttributes` Map(LowCardinality(String), String),
    `ProjectId` LowCardinality(String),
    `Flags` UInt32,
    `MetricSource` LowCardinality(String)
) AS
SELECT
    TimeUnix AS Timestamp,
    ServiceName,
    MetricName,
    Value,
    CAST(NULL AS Nullable(UInt64)) AS Count,
    CAST(NULL AS Nullable(Float64)) AS Sum,
    Attributes,
    ResourceAttributes,
    ProjectId,
    Flags,
    CAST('gauge' AS LowCardinality(String)) AS MetricSource
FROM otel.otel_metrics_gauge
UNION ALL
SELECT
    TimeUnix,
    ServiceName,
    MetricName,
    Value,
    CAST(NULL AS Nullable(UInt64)),
    CAST(NULL AS Nullable(Float64)),
    Attributes,
    ResourceAttributes,
    ProjectId,
    Flags,
    CAST('sum' AS LowCardinality(String))
FROM otel.otel_metrics_sum
UNION ALL
SELECT
    TimeUnix,
    ServiceName,
    MetricName,
    Sum AS Value,
    CAST(Count AS Nullable(UInt64)),
    CAST(Sum AS Nullable(Float64)),
    Attributes,
    ResourceAttributes,
    ProjectId,
    Flags,
    CAST('summary' AS LowCardinality(String))
FROM otel.otel_metrics_summary
UNION ALL
SELECT
    TimeUnix,
    ServiceName,
    MetricName,
    Sum AS Value,
    CAST(Count AS Nullable(UInt64)),
    CAST(Sum AS Nullable(Float64)),
    Attributes,
    ResourceAttributes,
    ProjectId,
    Flags,
    CAST('histogram' AS LowCardinality(String))
FROM otel.otel_metrics_histogram
UNION ALL
SELECT
    TimeUnix,
    ServiceName,
    MetricName,
    Sum AS Value,
    CAST(Count AS Nullable(UInt64)),
    CAST(Sum AS Nullable(Float64)),
    Attributes,
    ResourceAttributes,
    ProjectId,
    Flags,
    CAST('exp_histogram' AS LowCardinality(String))
FROM otel.otel_metrics_exp_histogram;

CREATE TABLE IF NOT EXISTS otel.stack_trace_events
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
    `Platform`              LowCardinality(String),      -- e.g. android/ios
    `OsVersion`             LowCardinality(String),
    `DeviceModel`           LowCardinality(String),
    `AppVersionCode`        LowCardinality(String),
    `AppVersion`            LowCardinality(String),
    `SdkVersion`            LowCardinality(String),
    `BundleId`              String,

    -- Tracing (stored as hex strings; ensure lower-case at ingest)
    `TraceId`               String,
    `SpanId`                FixedString(16),

    -- Grouping keys
    `GroupId`               String,
    `Signature`             String,
    `Fingerprint`           String,

    `ScopeAttributes`       Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `LogAttributes`         Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ResourceAttributes`    Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    -- CHANGED: TenantId replaced with ProjectId
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
    `PulseType` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['pulse.type'], 'otel'),
    `MeteringSessionId` String MATERIALIZED ifNull(LogAttributes['pulse.metering.session.id'], ''),
    INDEX idx_session_id SessionId TYPE bloom_filter(0.01) GRANULARITY 1
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(Timestamp)
-- CHANGED: ORDER BY now starts with ProjectId instead of TenantId
ORDER BY (ProjectId, GroupId, ExceptionType, toUnixTimestamp(Timestamp))
SETTINGS index_granularity = 8192;


CREATE TABLE IF NOT EXISTS otel.project_monthly_usage
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

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_logs_mv
TO otel.project_monthly_usage
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(Timestamp) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
FROM otel.otel_logs
GROUP BY project_id, month, source;

-- MV 2: Traces (events + sessions)
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_traces_mv
TO otel.project_monthly_usage
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(Timestamp) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
FROM otel.otel_traces
GROUP BY project_id, month, source;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_metrics_gauge_mv
TO otel.project_monthly_usage
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(TimeUnix) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
FROM otel.otel_metrics_gauge
GROUP BY project_id, month, source;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_metrics_sum_mv
TO otel.project_monthly_usage
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(TimeUnix) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
FROM otel.otel_metrics_sum
GROUP BY project_id, month, source;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_metrics_summary_mv
TO otel.project_monthly_usage
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(TimeUnix) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
FROM otel.otel_metrics_summary
GROUP BY project_id, month, source;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_metrics_histogram_mv
TO otel.project_monthly_usage
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(TimeUnix) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
FROM otel.otel_metrics_histogram
GROUP BY project_id, month, source;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_metrics_exp_histogram_mv
TO otel.project_monthly_usage
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(TimeUnix) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
FROM otel.otel_metrics_exp_histogram
GROUP BY project_id, month, source;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_stack_traces_events_mv
TO otel.project_monthly_usage
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(Timestamp) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
FROM otel.stack_trace_events
GROUP BY project_id, month, source;

CREATE TABLE IF NOT EXISTS otel.root_cause_cache
(
    `ProjectId`       LowCardinality(String) CODEC(ZSTD(1)),
    `interaction_name` LowCardinality(String) CODEC(ZSTD(1)),
    `date`             Date,
    `window_end_utc`   DateTime64(3, 'UTC') COMMENT 'Exclusive upper bound of RCA query window' CODEC(ZSTD(1)),
    `mode`             LowCardinality(String) COMMENT 'hierarchical | flat' CODEC(ZSTD(1)),
    `baseline`         String COMMENT 'JSON' CODEC(ZSTD(1)),
    `segments`         String COMMENT 'JSON' CODEC(ZSTD(1)),
    `cached_at`        DateTime64(3, 'UTC') CODEC(ZSTD(1))
)
ENGINE = ReplacingMergeTree(cached_at)
PARTITION BY toYYYYMM(date)
ORDER BY (ProjectId, interaction_name, date)
SETTINGS index_granularity = 8192;


CREATE TABLE IF NOT EXISTS otel.interaction_heatmaps_daily (
    `Date` Date,
    `ProjectId` LowCardinality(String),
    `ScreenName` LowCardinality(String),
    `AppVersion` LowCardinality(String),
    `Platform` LowCardinality(String),
    `GeographicalRegion` LowCardinality(String),
    `Breakpoint` LowCardinality(String),
    `XBin` Float32,
    `YBin` Float32,
    `WeightNormal` UInt64,
    `WeightRage` UInt64,
    `WeightDead` UInt64
)
ENGINE = SummingMergeTree()
ORDER BY (Date, ProjectId, ScreenName, AppVersion, Platform, GeographicalRegion, Breakpoint, XBin, YBin);


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.interaction_heatmaps_daily_mv
TO otel.interaction_heatmaps_daily AS
SELECT
    toDate(Timestamp) AS Date,
    ProjectId,
    ScreenName,
    AppVersion,
    Platform,
    GeoState AS GeographicalRegion,
    multiIf(
        Platform = 'Web' AND ViewportWidth > 1024, 'Web_Extra_Large',
        Platform = 'Web', 'Mobile_Medium',
        ViewportWidth > 600, 'Tablet_Large',
        ViewportWidth <= 600 AND (ViewportHeight / ViewportWidth) <= 1.5, 'Mobile_Small',
        'Mobile_Medium'
    ) AS Breakpoint,
    round(NormXPer, 2) AS XBin,
    round(NormYPer, 2) AS YBin,
    count() AS WeightNormal,
    countIf(Rage) AS WeightRage,
    countIf(ClickType = 'dead') AS WeightDead
FROM otel.otel_logs
WHERE PulseType = 'app.click'
GROUP BY Date, ProjectId, ScreenName, AppVersion, Platform, GeoState, Breakpoint, XBin, YBin;
 CREATE TABLE IF NOT EXISTS otel._lint_test_bad
(
    project_id String
)
ENGINE = MergeTree
ORDER BY tuple();