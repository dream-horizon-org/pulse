CREATE TABLE IF NOT EXISTS otel.stack_trace_events
ON CLUSTER 'pulse-clickhouse'
(
    Timestamp DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)) COMMENT 'event time (ns precision, store UTC)',
    EventName LowCardinality(String) CODEC(ZSTD(1)),
    Title String CODEC(ZSTD(3)),
    ExceptionStackTrace String CODEC(ZSTD(3)),
    ExceptionStackTraceRaw String CODEC(ZSTD(3)),
    ExceptionMessage String CODEC(ZSTD(3)),
    ExceptionType LowCardinality(String) CODEC(ZSTD(1)),
    Interactions Array(LowCardinality(String)) CODEC(ZSTD(1)),
    ScreenName LowCardinality(String) CODEC(ZSTD(1)),
    UserId String CODEC(ZSTD(1)),
    SessionId String CODEC(ZSTD(1)),
    Platform LowCardinality(String) CODEC(ZSTD(1)),
    OsVersion LowCardinality(String) CODEC(ZSTD(1)),
    DeviceModel LowCardinality(String) CODEC(ZSTD(1)),
    AppVersionCode LowCardinality(String) CODEC(ZSTD(1)),
    AppVersion LowCardinality(String) CODEC(ZSTD(1)),
    SdkVersion LowCardinality(String) CODEC(ZSTD(1)),
    BundleId String CODEC(ZSTD(1)),
    TraceId String CODEC(ZSTD(1)),
    SpanId FixedString(16) CODEC(ZSTD(1)),
    GroupId String CODEC(ZSTD(1)),
    Signature String CODEC(ZSTD(1)),
    Fingerprint String CODEC(ZSTD(1)),
    ScopeAttributes Map(LowCardinality(String), String) CODEC(ZSTD(3)),
    LogAttributes Map(LowCardinality(String), String) CODEC(ZSTD(3)),
    ResourceAttributes Map(LowCardinality(String), String) CODEC(ZSTD(3)),
    ProjectId LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], '') CODEC(ZSTD(1)),
    PulseType LowCardinality(String) MATERIALIZED ifNull(LogAttributes['pulse.type'], 'otel') CODEC(ZSTD(1)),
    MeteringSessionId String MATERIALIZED ifNull(LogAttributes['pulse.metering.session.id'], '') CODEC(ZSTD(1)),

    INDEX idx_session_id       SessionId          TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_group_id         GroupId            TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_fingerprint      Fingerprint        TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_user_id          UserId             TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_metering_session MeteringSessionId  TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_span_id          SpanId             TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_exception_type   ExceptionType      TYPE set(256)           GRANULARITY 1,
    INDEX idx_pulse_type       PulseType          TYPE set(16)            GRANULARITY 1,
    INDEX idx_platform         Platform           TYPE set(8)             GRANULARITY 1,
    INDEX idx_os_version       OsVersion          TYPE set(256)           GRANULARITY 1,
    INDEX idx_timestamp        Timestamp          TYPE minmax             GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/stack_trace_events_local', '{replica}')
PARTITION BY toYYYYMMDD(Timestamp)
ORDER BY (ProjectId, GroupId, ExceptionType, Timestamp)
TTL toDateTime(Timestamp) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(Timestamp) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';