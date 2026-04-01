-- =============================================================================
-- ClickHouse: Pre-computed journey results (Spark → ClickHouse)
-- =============================================================================
-- Journey definitions live in **MySQL**. Spark reads definitions + S3 Parquet and
-- writes aggregated path-graph rows here.
--
-- **Engine:** Single-node `MergeTree` — for local Docker / dev without ZooKeeper.
-- For **multi-replica** production, use `clickhouse-funnel-journey-replicated-schema.sql`.
--
-- Column names: **PascalCase** (consistent with other `otel.*` tables).
-- ENTRY: PosFrom = -1, EventFrom = ''. Anchor at PosTo = 0.
-- Read via API (e.g. GET /v1/journey/{id}/results).
-- =============================================================================

CREATE TABLE IF NOT EXISTS otel.journey_results
(
    `JourneyId`   UInt64 COMMENT 'MySQL journey.id',
    `ProjectId`   LowCardinality(String) COMMENT 'Project ID',
    `RunTime`     DateTime64(3, 'UTC') COMMENT 'Spark job execution time (UTC)',
    `Direction`   LowCardinality(String) COMMENT 'START | END',
    `PosFrom`     Int32 COMMENT 'Source path position; -1 = ENTRY',
    `EventFrom`   String CODEC(ZSTD(1)) COMMENT 'Event at PosFrom; empty = ENTRY',
    `PosTo`       Int32 COMMENT 'Destination path position',
    `EventTo`     LowCardinality(String) COMMENT 'Event at PosTo',
    `UserCount`   UInt64 COMMENT 'Distinct users or sessions on this edge',
    `CreatedAt`   DateTime64(3, 'UTC') DEFAULT now64(3) COMMENT 'Row insert time (UTC)'
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(toDate(RunTime))
ORDER BY (ProjectId, JourneyId, RunTime, Direction, PosFrom, EventFrom, PosTo, EventTo)
SETTINGS index_granularity = 8192;
