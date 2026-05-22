CREATE TABLE IF NOT EXISTS otel.funnel_results
(
    FunnelId           UInt64                 COMMENT 'MySQL funnel.id'                                      CODEC(T64, ZSTD(1)),
    ProjectId          LowCardinality(String) COMMENT 'Project ID (proj-xxx)'                                CODEC(ZSTD(1)),
    RunTime            DateTime64(3, 'UTC')   COMMENT 'Spark job execution time (UTC)'                       CODEC(DoubleDelta, ZSTD(1)),
    StepIndex          UInt8                  COMMENT '0-based step index'                                   CODEC(T64, ZSTD(1)),
    StepName           LowCardinality(String) COMMENT 'Event name for this step'                             CODEC(ZSTD(1)),
    UserCount          UInt64                 COMMENT 'Unique users or sessions reaching this step'          CODEC(T64, ZSTD(1)),
    ConversionPct      Float64                COMMENT 'Conversion % from step 0 to this step'                CODEC(ZSTD(1)),
    MedianStepSeconds  Nullable(Int64)        COMMENT 'Median seconds from previous step; NULL for step 0'   CODEC(T64, ZSTD(1)),
    OrderCount         Nullable(UInt64)       COMMENT 'Orders at revenue step (Spark AOV)'                   CODEC(T64, ZSTD(1)),
    Revenue            Nullable(Decimal(18, 4)) COMMENT 'Total revenue at revenue step (Spark AOV)'          CODEC(ZSTD(1)),
    AvgOrderValue      Nullable(Decimal(18, 4)) COMMENT 'Global average order value for run (Spark AOV)'     CODEC(ZSTD(1)),
    LostRevenue        Nullable(Decimal(18, 4)) COMMENT 'Drop-off × AOV at step (Spark AOV)'                 CODEC(ZSTD(1)),
    CreatedAt          DateTime64(3, 'UTC')   DEFAULT now64(3) COMMENT 'Row insert time (UTC)'               CODEC(DoubleDelta, ZSTD(1)),

    CONSTRAINT chk_StepIndex CHECK StepIndex < 32,

    INDEX idx_run_time    RunTime    TYPE minmax              GRANULARITY 1,
    INDEX idx_created_at  CreatedAt  TYPE minmax              GRANULARITY 1,
    INDEX idx_step_name   StepName   TYPE bloom_filter(0.01)  GRANULARITY 1
    )
ENGINE = MergeTree
PARTITION BY toYYYYMM(toDate(RunTime))
ORDER BY (ProjectId, FunnelId, RunTime, StepIndex)
SETTINGS index_granularity = 8192;