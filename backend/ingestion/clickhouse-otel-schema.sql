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
    `SpanType` LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['pulse.type'], ''), // DEPRECATED: Use PulseType instead
    `PulseType` LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['pulse.type'], ''),
    `SessionId` String MATERIALIZED ifNull(SpanAttributes['session.id'], ''),
    `AppVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['app.build_name'], ''),
    `SDKVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], ''), // TBD
    `Platform` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], ''),
    `OsVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], ''),
    `GeoState` LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['geo.region.iso_code'], ''),
    `GeoCountry` LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['geo.country.iso_code'], ''),
    `DeviceModel` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], ''),
    `NetworkProvider` LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['network.carrier.name'], ''),
    `UserId` String MATERIALIZED ifNull(SpanAttributes['user.id'], ''), 
    INDEX idx_trace_id TraceId TYPE bloom_filter(0.001) GRANULARITY 1,
    INDEX idx_user_id UserId TYPE bloom_filter(0.001) GRANULARITY 1
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(Timestamp)
ORDER BY (ServiceName, PulseType, SpanName, Timestamp)
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
    `AppVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['app.build_name'], ''),
    `SDKVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], ''), // TBD
    `Platform` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], ''),
    `OsVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], ''),
    `GeoState` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['geo.region.iso_code'], ''),
    `GeoCountry` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['geo.country.iso_code'], ''),
    `DeviceModel` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], ''),
    `NetworkProvider` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['network.carrier.name'], ''),
    `UserId` String MATERIALIZED ifNull(LogAttributes['user.id'], ''),
    `PulseType` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['pulse.type'], 'otel'),
    `EventName` LowCardinality(String) CODEC(ZSTD(1)),
    INDEX idx_trace_id TraceId TYPE bloom_filter(0.001) GRANULARITY 1
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(Timestamp)
ORDER BY (ServiceName, PulseType, EventName, SeverityText, toUnixTimestamp(Timestamp), TraceId)
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
    `SessionId` String MATERIALIZED ifNull(Attributes['session.id'], ''),
    `AppVersion` LowCardinality(String) MATERIALIZED ifNull(Attributes['app.build_name'], ''),
    `SDKVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], ''),
    `Platform` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], ''),
    `OsVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], ''),
    `GeoState` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.region.iso_code'], ''),
    `GeoCountry` LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.country.iso_code'], ''),
    `DeviceModel` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], ''),
    `NetworkProvider` LowCardinality(String) MATERIALIZED ifNull(Attributes['network.carrier.name'], ''),
    `UserId` String MATERIALIZED ifNull(Attributes['user.id'], ''),
    `Exemplars.FilteredAttributes` Array(Map(LowCardinality(String), String)) CODEC(ZSTD(1)),
    `Exemplars.TimeUnix` Array(DateTime64(9)) CODEC(ZSTD(1)),
    `Exemplars.Value` Array(Float64) CODEC(ZSTD(1)),
    `Exemplars.SpanId` Array(String) CODEC(ZSTD(1)),
    `Exemplars.TraceId` Array(String) CODEC(ZSTD(1))
)
ENGINE = MergeTree
PARTITION BY toDate(TimeUnix)
ORDER BY (ServiceName, MetricName, Attributes, toUnixTimestamp64Nano(TimeUnix))
SETTINGS index_granularity = 8192;

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
    `PulseType` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['pulse.type'], 'otel')
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(Timestamp)
ORDER BY (GroupId, ExceptionType, toUnixTimestamp(Timestamp))
SETTINGS index_granularity = 8192;