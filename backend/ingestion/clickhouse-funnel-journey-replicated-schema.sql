-- =============================================================================
-- ClickHouse: Funnel + journey results — REPLICATED cluster DDL
-- =============================================================================
-- Use on a **ClickHouse cluster** with ZooKeeper / ClickHouse Keeper and macros
-- `{shard}`, `{replica}` (see deploy/terraform/clickhouse macros).
--
-- Creates:
--   * otel.funnel_results_local, otel.journey_results_local — ReplicatedMergeTree
--   * otel.funnel_results, otel.journey_results — Distributed (what Spark/API target)
--
-- Column names: **PascalCase** (consistent with single-node DDL and `otel.*` OTEL tables).
--
-- **Do not** run this on single-node Docker dev (no cluster / no Keeper) — use
-- clickhouse-funnel-results-schema.sql and clickhouse-journey-results-schema.sql
-- instead.
--
-- Shard routing: same as other OTEL Distributed tables (hash of entity id).
-- =============================================================================

CREATE DATABASE IF NOT EXISTS otel ON CLUSTER `pulse-clickhouse`;

-- ---------------------------------------------------------------------------
-- Funnel results
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS otel.funnel_results_local
ON CLUSTER `pulse-clickhouse`
(
    `FunnelId`      UInt64 COMMENT 'MySQL funnel.id',
    `ProjectId`     LowCardinality(String) COMMENT 'Project ID (proj-xxx)',
    `RunTime`       DateTime64(3, 'UTC') COMMENT 'Spark job execution time (UTC)',
    `StepIndex`     UInt8 COMMENT '0-based step index',
    `StepName`      LowCardinality(String) COMMENT 'Event name for this step',
    `UserCount`     UInt64 COMMENT 'Unique users or sessions reaching this step',
    `ConversionPct` Float64 COMMENT 'Conversion % from step 0 to this step' CODEC(ZSTD(1)),
    `CreatedAt`     DateTime64(3, 'UTC') DEFAULT now64(3) COMMENT 'Row insert time (UTC)',
    CONSTRAINT chk_StepIndex_local CHECK StepIndex < 32
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/funnel_results_local', '{replica}')
PARTITION BY toYYYYMM(toDate(RunTime))
ORDER BY (ProjectId, FunnelId, RunTime, StepIndex)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.funnel_results
ON CLUSTER `pulse-clickhouse`
AS otel.funnel_results_local
ENGINE = Distributed(`pulse-clickhouse`, otel, funnel_results_local, cityHash64(FunnelId));

-- ---------------------------------------------------------------------------
-- Journey results
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS otel.journey_results_local
ON CLUSTER `pulse-clickhouse`
(
    `JourneyId`   UInt64 COMMENT 'MySQL journey.id',
    `ProjectId`   LowCardinality(String) COMMENT 'Project ID',
    `RunTime`     DateTime64(3, 'UTC') COMMENT 'Spark job execution time (UTC)',
    `Direction`   LowCardinality(String) COMMENT 'START | END',
    `PosFrom`     Int32 COMMENT 'Source path position; -1 = ENTRY',
    `EventFrom`   String COMMENT 'Event at PosFrom; empty = ENTRY' CODEC(ZSTD(1)),
    `PosTo`       Int32 COMMENT 'Destination path position',
    `EventTo`     LowCardinality(String) COMMENT 'Event at PosTo',
    `UserCount`   UInt64 COMMENT 'Distinct users or sessions on this edge',
    `CreatedAt`   DateTime64(3, 'UTC') DEFAULT now64(3) COMMENT 'Row insert time (UTC)'
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/journey_results_local', '{replica}')
PARTITION BY toYYYYMM(toDate(RunTime))
ORDER BY (ProjectId, JourneyId, RunTime, Direction, PosFrom, EventFrom, PosTo, EventTo)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.journey_results
ON CLUSTER `pulse-clickhouse`
AS otel.journey_results_local
ENGINE = Distributed(`pulse-clickhouse`, otel, journey_results_local, cityHash64(JourneyId));
