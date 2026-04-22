CREATE TABLE IF NOT EXISTS otel.session_summary
ON CLUSTER 'pulse-clickhouse'
(
    ProjectId           LowCardinality(String) CODEC(ZSTD(1)),
    sessionId           String                 CODEC(ZSTD(1)),
    startTime           SimpleAggregateFunction(min, DateTime64(9, 'UTC')) CODEC(DoubleDelta, ZSTD(1)),
    endTime             SimpleAggregateFunction(max, DateTime64(9, 'UTC')) CODEC(DoubleDelta, ZSTD(1)),
    userId              SimpleAggregateFunction(any, String)                CODEC(ZSTD(1)),
    platform            SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    appVersion          SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    osVersion           SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    deviceModel         SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    networkProvider     SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    geoCountry          SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    geoRegion           SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    apdexSum            SimpleAggregateFunction(sum, Float64) CODEC(ZSTD(1)),
    apdexCount          SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    networkErrors       SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    interactionErrors   SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    crashCount          SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    anrCount            SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    nonFatal            SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    slowInteractionCount SimpleAggregateFunction(sum, UInt64) CODEC(T64, ZSTD(1)),
    frozenFrameCount    SimpleAggregateFunction(sum, Float64) CODEC(ZSTD(1)),
    spanCount           SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),

    INDEX idx_user_id     userId      TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_start_time  startTime   TYPE minmax             GRANULARITY 1,
    INDEX idx_end_time    endTime     TYPE minmax             GRANULARITY 1,
    INDEX idx_crash_count crashCount  TYPE minmax             GRANULARITY 1,
    INDEX idx_anr_count   anrCount    TYPE minmax             GRANULARITY 1,
    INDEX idx_nonfatal    nonFatal    TYPE minmax             GRANULARITY 1
)
ENGINE = ReplicatedAggregatingMergeTree('/clickhouse/tables/{shard}/otel/session_summary', '{replica}')
PARTITION BY (toYYYYMMDD(startTime))
ORDER BY (ProjectId, sessionId)
TTL toDateTime(startTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(startTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';