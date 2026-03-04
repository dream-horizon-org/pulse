-- =============================================================================
-- Pulse Session Replay - ClickHouse Schema
-- =============================================================================
-- This schema stores session replay metadata with S3 block URLs for byte-range
-- access. The actual recording data lives in S3 as Snappy-compressed JSONL.
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
    session_id          String,
    project_id          LowCardinality(String),
    user_id             String,
    first_timestamp     DateTime64(6, 'UTC'),
    last_timestamp      DateTime64(6, 'UTC'),
    block_url           Nullable(String),
    size                Int64,
    snapshot_source     LowCardinality(Nullable(String)),
    snapshot_library    Nullable(String)
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
--      - sum for size
--    This allows late-arriving blocks to be correctly merged.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS otel.session_replay_events
(
    session_id              String,
    project_id              LowCardinality(String),
    user_id                 String,
    min_first_timestamp     SimpleAggregateFunction(min, DateTime64(6, 'UTC')),
    max_last_timestamp      SimpleAggregateFunction(max, DateTime64(6, 'UTC')),
    -- S3 block URLs with byte-range offsets and their corresponding timestamps
    block_first_timestamps  SimpleAggregateFunction(groupArrayArray, Array(DateTime64(6, 'UTC'))),
    block_last_timestamps   SimpleAggregateFunction(groupArrayArray, Array(DateTime64(6, 'UTC'))),
    block_urls              SimpleAggregateFunction(groupArrayArray, Array(String)),
    -- Aggregate size across all blocks
    size                    SimpleAggregateFunction(sum, Int64),
    -- Source of recording (mobile/web) — takes the value from the earliest block
    snapshot_source         AggregateFunction(argMin, LowCardinality(Nullable(String)), DateTime64(6, 'UTC')),
    snapshot_library        AggregateFunction(argMin, Nullable(String), DateTime64(6, 'UTC')),
    _timestamp              SimpleAggregateFunction(max, DateTime)
) ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(min_first_timestamp)
ORDER BY (toDate(min_first_timestamp), project_id, user_id, session_id)
SETTINGS index_granularity = 512;

-- -----------------------------------------------------------------------------
-- 3. Materialized View
--    Automatically transforms each Kafka message (individual block metadata)
--    into the aggregated format and inserts into the storage table.
--    GROUP BY session_id, project_id, user_id ensures blocks for the same
--    session are aggregated together on merge.
-- -----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.session_replay_events_mv
TO otel.session_replay_events
AS SELECT
    session_id,
    project_id,
    any(user_id) as user_id,
    min(first_timestamp) AS min_first_timestamp,
    max(last_timestamp) AS max_last_timestamp,
    groupArray(if(block_url != '', first_timestamp, NULL)) AS block_first_timestamps,
    groupArray(if(block_url != '', last_timestamp, NULL)) AS block_last_timestamps,
    groupArray(block_url) AS block_urls,
    sum(size) as size,
    argMinState(snapshot_source, first_timestamp) as snapshot_source,
    argMinState(snapshot_library, first_timestamp) as snapshot_library,
    now() as _timestamp
FROM otel.kafka_session_replay_events
GROUP BY session_id, project_id;
