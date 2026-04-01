-- =============================================================================
-- ClickHouse: Pre-computed funnel results (Spark → ClickHouse)
-- =============================================================================
-- Funnel **definitions** live in **MySQL**. Spark reads definitions + S3 Parquet
-- and writes **aggregated** rows here only.
--
-- **Engine:** Single-node `MergeTree` — for local Docker / dev without ZooKeeper.
-- For **multi-replica** production clusters, create `funnel_results_local` +
-- `funnel_results` from `clickhouse-funnel-journey-replicated-schema.sql` instead
-- (or migrate with `EXCHANGE TABLES`).
--
-- Column names: **PascalCase** (consistent with `otel.otel_traces`, etc.).
-- Read via API (e.g. GET /v1/funnel/{id}/results).
-- =============================================================================

CREATE TABLE IF NOT EXISTS otel.funnel_results
(
    `FunnelId`      UInt64 COMMENT 'MySQL funnel.id',
    `ProjectId`     LowCardinality(String) COMMENT 'Project ID (proj-xxx)',
    `RunTime`       DateTime64(3, 'UTC') COMMENT 'Spark job execution time (UTC)',
    `StepIndex`     UInt8 COMMENT '0-based step index',
    `StepName`      LowCardinality(String) COMMENT 'Event name for this step',
    `UserCount`     UInt64 COMMENT 'Unique users or sessions reaching this step',
    `ConversionPct` Float64 COMMENT 'Conversion % from step 0 to this step' CODEC(ZSTD(1)),
    `CreatedAt`     DateTime64(3, 'UTC') DEFAULT now64(3) COMMENT 'Row insert time (UTC)',
    CONSTRAINT chk_StepIndex CHECK StepIndex < 32
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(toDate(RunTime))
ORDER BY (ProjectId, FunnelId, RunTime, StepIndex)
SETTINGS index_granularity = 8192;

-- Optional: project-scoped queries
-- ALTER TABLE otel.funnel_results ADD INDEX idx_project ProjectId TYPE bloom_filter(0.01) GRANULARITY 4;
