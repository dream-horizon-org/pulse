-- =============================================================================
-- ClickHouse: Pre-computed funnel results ONLY (Spark → ClickHouse)
-- =============================================================================
-- Funnel **definitions** are stored in **MySQL** (see Confluence: Funnel & User
-- Journey Schema Design, page 4787011590). Spark reads definitions + S3 Parquet
-- and writes **aggregated** rows here only.
--
-- Do **not** create otel.product_events or a raw “product events” table in
-- ClickHouse for this pipeline — raw events remain in S3 (Vector).
--
-- One row per (funnel_id, run_date, step_index). Spark pre-computes all saved
-- funnels (daily + on-save); pulse-server reads via GET /v1/funnel/{id}/results.
--
-- Doc: docs/architecture/funnel-mysql-clickhouse-schema.md
-- =============================================================================

CREATE TABLE IF NOT EXISTS otel.funnel_results
(
    funnel_id      String        COMMENT 'Same as MySQL funnel.funnel_id',
    project_id     String        COMMENT 'Project ID (proj-xxx)',
    run_time       DateTime64(3, 'UTC') COMMENT 'Execution time of the Spark job',
    step_index     UInt8         COMMENT '0-based step index',
    step_name      String        COMMENT 'Event name for this step',
    user_count     UInt64        COMMENT 'Unique users (or sessions) reaching this step',
    conversion_pct Float64       COMMENT 'Conversion % from step 0 to this step',
    created_at     DateTime64(3) DEFAULT now64(3),
    CONSTRAINT chk_step_index CHECK step_index < 32
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(toDate(run_time))
ORDER BY (funnel_id, run_time, step_index)
SETTINGS index_granularity = 8192;

-- Optional: secondary index for project-scoped admin queries (uncomment if needed)
-- ALTER TABLE otel.funnel_results ADD INDEX idx_project project_id TYPE bloom_filter GRANULARITY 4;
