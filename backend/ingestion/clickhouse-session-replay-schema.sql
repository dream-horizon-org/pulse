-- =============================================================================
-- Pulse Session Replay - ClickHouse Schema (Replicated Cluster)
-- =============================================================================
-- This schema stores session replay metadata with S3 block URLs for byte-range
-- access. The actual recording data lives in S3 as compressed JSONL.
--
-- Data flow:
--   Node.js Ingestion Consumer → Kafka (clickhouse_session_replay_events)
--   → Kafka Engine Table → Materialized View → ReplicatedAggregatingMergeTree
-- =============================================================================

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS otel ON CLUSTER `pulse-clickhouse`;

-- Local Kafka table (reads from Kafka on each node)
CREATE TABLE IF NOT EXISTS otel.kafka_session_replay_events_local
ON CLUSTER `pulse-clickhouse`
(
    `SessionId`       String,
    `ProjectId`       LowCardinality(String),
    `UserId`          String,
    `FirstTimestamp`  DateTime64(6, 'UTC'),
    `LastTimestamp`   DateTime64(6, 'UTC'),
    `BlockUrl`        String,
    `SnapshotSource`  LowCardinality(String)
) ENGINE = Kafka
SETTINGS 
    kafka_broker_list = 'pulse-kafka-01.pulse.local:9092,pulse-kafka-02.pulse.local:9092',
    kafka_topic_list = 'clickhouse_session_replay_events',
    kafka_group_name = 'pulse_ch_session_replay_final',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 6;

-- Storage table (replicated)
CREATE TABLE IF NOT EXISTS otel.session_replay_events_local
ON CLUSTER `pulse-clickhouse`
(
    `SessionId`             String CODEC(ZSTD(1)),
    `ProjectId`             LowCardinality(String) CODEC(ZSTD(1)),
    `UserId`                String CODEC(ZSTD(1)),
    `MinFirstTimestamp`    SimpleAggregateFunction(min, DateTime64(6, 'UTC')) CODEC(Delta, ZSTD(1)),
    `MaxLastTimestamp`     SimpleAggregateFunction(max, DateTime64(6, 'UTC')) CODEC(Delta, ZSTD(1)),
    `BlockUrls`             SimpleAggregateFunction(groupArrayArray, Array(String)) CODEC(ZSTD(1)),
    `BlockFirstTimestamps` SimpleAggregateFunction(groupArrayArray, Array(DateTime64(6, 'UTC'))) CODEC(ZSTD(1)),
    `BlockLastTimestamps`  SimpleAggregateFunction(groupArrayArray, Array(DateTime64(6, 'UTC'))) CODEC(ZSTD(1)),
    `SnapshotSource`        AggregateFunction(argMin, LowCardinality(String), DateTime64(6, 'UTC'))
) ENGINE = ReplicatedAggregatingMergeTree('/clickhouse/tables/{shard}/otel/session_replay_events_local', '{replica}')
PARTITION BY (ProjectId, toYYYYMMDD(MinFirstTimestamp))
ORDER BY (ProjectId, SessionId)
TTL toDateTime(MaxLastTimestamp) + INTERVAL 90 DAY
SETTINGS merge_with_ttl_timeout = 86400;

-- Distributed table (queries hit this)
CREATE TABLE IF NOT EXISTS otel.session_replay_events
ON CLUSTER `pulse-clickhouse`
AS otel.session_replay_events_local
ENGINE = Distributed(`pulse-clickhouse`, otel, session_replay_events_local, cityHash64(SessionId));

-- Materialized view (on each node, reads from Kafka → writes to local table)
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.session_replay_events_mv
ON CLUSTER `pulse-clickhouse`
TO otel.session_replay_events_local
AS SELECT
    SessionId,
    ProjectId,
    any(UserId) AS UserId,
    min(FirstTimestamp)        AS MinFirstTimestamp,
    max(LastTimestamp)         AS MaxLastTimestamp,
    groupArray(BlockUrl)       AS BlockUrls,
    groupArray(FirstTimestamp) AS BlockFirstTimestamps,
    groupArray(LastTimestamp)  AS BlockLastTimestamps,
    argMinState(SnapshotSource, FirstTimestamp) AS SnapshotSource
FROM otel.kafka_session_replay_events_local
GROUP BY SessionId, ProjectId;
