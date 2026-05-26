CREATE TABLE IF NOT EXISTS otel.funnel_session_state
(
    FunnelId             UInt64                 COMMENT 'MySQL funnel.id'                                                CODEC(T64, ZSTD(1)),
    ProjectId            LowCardinality(String) COMMENT 'Project ID (proj-xxx)'                                           CODEC(ZSTD(1)),
    RunTime              DateTime64(3, 'UTC')   COMMENT 'Spark / CH job execution time (matches funnel_results.RunTime)' CODEC(DoubleDelta, ZSTD(1)),
    SessionId            String                 COMMENT 'OTel SessionId — the join key to all OTel tables'                CODEC(ZSTD(1)),
    UserId               String                 COMMENT 'User at the time of the furthest step (may be empty)'            CODEC(ZSTD(1)),
    LastReachedStep      UInt8                  COMMENT '0-based index of the furthest step reached'                     CODEC(T64, ZSTD(1)),
    LastReachedStepName  LowCardinality(String) COMMENT 'Event name for LastReachedStep'                                  CODEC(ZSTD(1)),
    LastReachedAt        DateTime64(3, 'UTC')   COMMENT 'Timestamp of the LastReachedStep — anchor for OTel window joins' CODEC(DoubleDelta, ZSTD(1)),
    DropoffStep          Int8                   COMMENT '-1 if converted (reached final step); else LastReachedStep + 1'  CODEC(T64, ZSTD(1)),
    TimeToDropoffSec     Int64                  COMMENT 'Seconds from step 0 to LastReachedAt'                            CODEC(T64, ZSTD(1)),
    ScreenAtDropoff      LowCardinality(String) COMMENT 'Screen active at LastReachedAt, for heatmap drill-in'           CODEC(ZSTD(1)),
    TraceIdAtDropoff     String                 COMMENT 'Trace ID at LastReachedAt, for waterfall drill-in'              CODEC(ZSTD(1)),
    AppVersion           LowCardinality(String)                                                                          CODEC(ZSTD(1)),
    OsName               LowCardinality(String)                                                                          CODEC(ZSTD(1)),
    OsVersion            LowCardinality(String)                                                                          CODEC(ZSTD(1)),
    Platform             LowCardinality(String)                                                                          CODEC(ZSTD(1)),
    DeviceModel          LowCardinality(String)                                                                          CODEC(ZSTD(1)),
    NetworkProvider      LowCardinality(String)                                                                          CODEC(ZSTD(1)),
    GeoCountry           LowCardinality(String)                                                                          CODEC(ZSTD(1)),
    CreatedAt            DateTime64(3, 'UTC')   DEFAULT now64(3) COMMENT 'Row insert time (UTC)'                          CODEC(DoubleDelta, ZSTD(1)),

    INDEX idx_session_id   SessionId   TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_user_id      UserId      TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_run_time     RunTime     TYPE minmax             GRANULARITY 1,
    INDEX idx_dropoff_step DropoffStep TYPE set(32)            GRANULARITY 1,
    INDEX idx_app_version  AppVersion  TYPE bloom_filter(0.01) GRANULARITY 1
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(RunTime)
ORDER BY (ProjectId, FunnelId, RunTime, DropoffStep, SessionId)
SETTINGS index_granularity = 8192;
