-- =============================================================================
-- ClickHouse: Pre-computed funnel results (written by Spark, read by pulse-server)
-- =============================================================================
-- One row per (funnel_id, run_date, step_index). Spark job (on-save + daily)
-- writes step-level counts here; dashboard reads via GET /v1/funnel/{id}/results.
-- =============================================================================

CREATE TABLE IF NOT EXISTS otel.funnel_results
(
    funnel_id      String        COMMENT 'Same as MySQL funnel.funnel_id',
    project_id     String        COMMENT 'Project ID (proj-xxx)',
    run_date       Date          COMMENT 'Date of the data window (report date)',
    step_index     UInt8         COMMENT '0-based step index',
    step_name      String        COMMENT 'Event name for this step',
    user_count     UInt64        COMMENT 'Unique users (or sessions) reaching this step',
    conversion_pct Float64       COMMENT 'Conversion % from step 0 to this step',
    created_at     DateTime64(3) DEFAULT now64(3),
    CONSTRAINT chk_step_index CHECK step_index < 32
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(run_date)
ORDER BY (funnel_id, run_date, step_index)
SETTINGS index_granularity = 8192;
