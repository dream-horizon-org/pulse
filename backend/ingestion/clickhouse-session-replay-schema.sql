-- =============================================================================
-- Pulse Session Replay - ClickHouse Schema
-- =============================================================================
-- This schema stores session replay metadata with S3 block URLs for byte-range
-- access. The actual recording data lives in S3 as compressed JSONL.
--
-- Data flow:
--   Node.js Ingestion Consumer → Kafka (clickhouse_session_replay_events)
--   → Kafka Engine Table → Materialized View → AggregatingMergeTree
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Kafka Engine Table
--    Reads individual block metadata events from Kafka in JSONEachRow format.
--    Each row represents one session block that was flushed to S3.
-- -----------------------------------------------------------------------------
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
    kafka_broker_list = 'pulse-kafka-01.pulse.local:9092,pulse-kafka-02.pulse.local:9092',
    kafka_topic_list = 'clickhouse_session_replay_events',
    kafka_group_name = 'pulse_ch_session_replay_consumer',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 2;

-- -----------------------------------------------------------------------------
-- 2. Storage Table (AggregatingMergeTree)
--    Stores aggregated session metadata. Multiple blocks for the same session
--    are merged together using SimpleAggregateFunctions:
--      - min/max for timestamps
--      - groupArrayArray to append block URLs and their timestamps
--    This allows late-arriving blocks to be correctly merged.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS otel.session_replay_events
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
) ENGINE = AggregatingMergeTree()
PARTITION BY (ProjectId, toYYYYMMDD(MinFirstTimestamp))
ORDER BY (ProjectId, SessionId)
TTL toDateTime(MaxLastTimestamp) + INTERVAL 90 DAY
SETTINGS merge_with_ttl_timeout = 86400;

-- -----------------------------------------------------------------------------
-- 3. Materialized View
--    Automatically transforms each Kafka message (individual block metadata)
--    into the aggregated format and inserts into the storage table.
--    GROUP BY session_id, project_id ensures blocks for the same session
--    are aggregated together on merge.
-- -----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.session_replay_events_mv
TO otel.session_replay_events
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
FROM otel.kafka_session_replay_events
GROUP BY SessionId, ProjectId;