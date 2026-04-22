CREATE TABLE IF NOT EXISTS otel.session_replay_events
    ON CLUSTER 'pulse-clickhouse'
(
    SessionId            String                 CODEC(ZSTD(1)),
    ProjectId            LowCardinality(String) CODEC(ZSTD(1)),
    UserId               String                 CODEC(ZSTD(1)),
    MinFirstTimestamp    SimpleAggregateFunction(min, DateTime64(6, 'UTC')) CODEC(DoubleDelta, ZSTD(1)),
    MaxLastTimestamp     SimpleAggregateFunction(max, DateTime64(6, 'UTC')) CODEC(DoubleDelta, ZSTD(1)),
    BlockUrls            SimpleAggregateFunction(groupArrayArray, Array(String))               CODEC(ZSTD(3)),
    BlockFirstTimestamps SimpleAggregateFunction(groupArrayArray, Array(DateTime64(6, 'UTC'))) CODEC(ZSTD(3)),
    BlockLastTimestamps  SimpleAggregateFunction(groupArrayArray, Array(DateTime64(6, 'UTC'))) CODEC(ZSTD(3)),
    SnapshotSource       AggregateFunction(argMin, LowCardinality(String), DateTime64(6, 'UTC')) CODEC(ZSTD(1)),

    INDEX idx_user_id            UserId            TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_min_first_ts       MinFirstTimestamp TYPE minmax             GRANULARITY 1,
    INDEX idx_max_last_ts        MaxLastTimestamp  TYPE minmax             GRANULARITY 1
)
ENGINE = ReplicatedAggregatingMergeTree('/clickhouse/tables/{shard}/otel/session_replay_events', '{replica}')
PARTITION BY (toYYYYMMDD(MinFirstTimestamp))
ORDER BY (ProjectId, SessionId)
TTL toDateTime(MinFirstTimestamp) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(MaxLastTimestamp)  + toIntervalDay(90) DELETE
SETTINGS merge_with_ttl_timeout = 86400, index_granularity = 8192, storage_policy = 'tiered';
