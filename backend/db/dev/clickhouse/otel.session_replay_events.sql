CREATE TABLE IF NOT EXISTS otel.kafka_session_replay_events
(
    `SessionId`       String,
    `ProjectId`       LowCardinality(String),
    `UserId`          String,
    `FirstTimestamp`  DateTime64(6, 'UTC'),
    `LastTimestamp`   DateTime64(6, 'UTC'),
    `BlockUrl`        String,
    `SnapshotSource`  LowCardinality(String)
) ENGINE = Kafka()
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'clickhouse_session_replay_events',
    kafka_group_name = 'pulse_ch_session_replay_consumer',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS otel.session_replay_events
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
ENGINE = AggregatingMergeTree
PARTITION BY (toYYYYMMDD(MinFirstTimestamp))
ORDER BY (ProjectId, SessionId)
SETTINGS merge_with_ttl_timeout = 86400, index_granularity = 8192;


CREATE MATERIALIZED VIEW otel.session_replay_events_mv TO otel.session_replay_events
(
    `SessionId` String,
    `ProjectId` LowCardinality(String),
    `UserId` String,
    `MinFirstTimestamp` DateTime64(6, 'UTC'),
    `MaxLastTimestamp` DateTime64(6, 'UTC'),
    `BlockUrls` Array(String),
    `BlockFirstTimestamps` Array(DateTime64(6, 'UTC')),
    `BlockLastTimestamps` Array(DateTime64(6, 'UTC')),
    `SnapshotSource` AggregateFunction(argMin, String, DateTime64(6, 'UTC'))
)
AS SELECT
            SessionId,
            ProjectId,
            any(UserId) AS UserId,
            min(FirstTimestamp) AS MinFirstTimestamp,
            max(LastTimestamp) AS MaxLastTimestamp,
            groupArray(BlockUrl) AS BlockUrls,
            groupArray(FirstTimestamp) AS BlockFirstTimestamps,
            groupArray(LastTimestamp) AS BlockLastTimestamps,
            argMinState(SnapshotSource, FirstTimestamp) AS SnapshotSource
   FROM otel.kafka_session_replay_events
   GROUP BY
            SessionId,
            ProjectId
