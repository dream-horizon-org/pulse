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
    session_id       String,
    project_id       LowCardinality(String),
    user_id          String,
    first_timestamp  DateTime64(6, 'UTC'),
    last_timestamp   DateTime64(6, 'UTC'),
    block_url        String,
    snapshot_source  LowCardinality(String)
) ENGINE = Kafka()
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'clickhouse_session_replay_events',
    kafka_group_name = 'pulse_ch_session_replay_consumer',
    kafka_format = 'JSONEachRow',
    kafka_num_consumers = 1;

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
    session_id             String CODEC(ZSTD(1)),
    project_id             LowCardinality(String) CODEC(ZSTD(1)),
    user_id                String CODEC(ZSTD(1)),
    min_first_timestamp    SimpleAggregateFunction(min, DateTime64(6, 'UTC')) CODEC(Delta, ZSTD(1)),
    max_last_timestamp     SimpleAggregateFunction(max, DateTime64(6, 'UTC')) CODEC(Delta, ZSTD(1)),
    block_urls             SimpleAggregateFunction(groupArrayArray, Array(String)) CODEC(ZSTD(1)),
    block_first_timestamps SimpleAggregateFunction(groupArrayArray, Array(DateTime64(6, 'UTC'))) CODEC(ZSTD(1)),
    block_last_timestamps  SimpleAggregateFunction(groupArrayArray, Array(DateTime64(6, 'UTC'))) CODEC(ZSTD(1)),
    snapshot_source        AggregateFunction(argMin, LowCardinality(String), DateTime64(6, 'UTC'))
) ENGINE = AggregatingMergeTree()
PARTITION BY (project_id, toYYYYMMDD(min_first_timestamp))
ORDER BY (project_id, session_id)
TTL toDateTime(max_last_timestamp) + INTERVAL 90 DAY
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
    session_id,
    project_id,
    any(user_id) AS user_id,
    min(first_timestamp)        AS min_first_timestamp,
    max(last_timestamp)         AS max_last_timestamp,
    groupArray(block_url)       AS block_urls,
    groupArray(first_timestamp) AS block_first_timestamps,
    groupArray(last_timestamp)  AS block_last_timestamps,
    argMinState(snapshot_source, first_timestamp) AS snapshot_source
FROM otel.kafka_session_replay_events
GROUP BY session_id, project_id;
