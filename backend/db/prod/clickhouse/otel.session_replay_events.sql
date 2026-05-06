CREATE TABLE IF NOT EXISTS otel.kafka_session_replay_events
    ON CLUSTER 'pulse-ch'
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

CREATE TABLE otel.session_replay_events_local
ON CLUSTER 'pulse-ch'
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


CREATE TABLE IF NOT EXISTS otel.session_replay_events
ON CLUSTER 'pulse-ch'
AS otel.session_replay_events_local
    ENGINE = Distributed('pulse-ch', otel, session_replay_events_local, cityHash64(SessionId));


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.session_replay_events_mv
       ON CLUSTER 'pulse-ch'
       TO otel.session_replay_events
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
   FROM otel.kafka_session_replay_events_local
   GROUP BY
            SessionId,
            ProjectId;
