CREATE TABLE IF NOT EXISTS otel.funnel_dropoff_attribution
(
    FunnelId          UInt64                 COMMENT 'MySQL funnel.id'                                                          CODEC(T64, ZSTD(1)),
    ProjectId         LowCardinality(String) COMMENT 'Project ID (proj-xxx)'                                                     CODEC(ZSTD(1)),
    RunTime           DateTime64(3, 'UTC')   COMMENT 'Job run time (matches funnel_results.RunTime)'                            CODEC(DoubleDelta, ZSTD(1)),
    StepIndex         UInt8                  COMMENT 'Drop-off step: users reached StepIndex-1, failed to reach StepIndex'      CODEC(T64, ZSTD(1)),
    CauseKind         LowCardinality(String) COMMENT 'crash | anr | non_fatal | http_5xx | http_4xx | frozen_frame'             CODEC(ZSTD(1)),
    CauseKey          String                 COMMENT 'Stable cause identifier (e.g. ExceptionType@Screen, method host status)'   CODEC(ZSTD(1)),
    CauseLabel        String                 COMMENT 'Human display label (UI never needs to compose one)'                       CODEC(ZSTD(3)),
    DropoffCohort     UInt64                 COMMENT 'Total droppers at this step (sessions or users per funnel mode)'           CODEC(T64, ZSTD(1)),
    DropoffAffected   UInt64                 COMMENT 'Droppers who hit this cause in the attribution window'                     CODEC(T64, ZSTD(1)),
    ConverterCohort   UInt64                 COMMENT 'Total converters (reached final step) — baseline denominator'              CODEC(T64, ZSTD(1)),
    ConverterAffected UInt64                 COMMENT 'Converters who hit this cause in the equivalent window'                    CODEC(T64, ZSTD(1)),
    Lift              Float64                COMMENT '(DropoffAffected/DropoffCohort) / (ConverterAffected/ConverterCohort)'    CODEC(ZSTD(1)),
    PValue            Float64                COMMENT 'Chi-square / Fisher p-value (currently stubbed to 0.0 — deferred)'         CODEC(ZSTD(1)),
    ExampleSessions   Array(String)          COMMENT 'Up to 50 session IDs for evidence drill-in'                                CODEC(ZSTD(3)),
    CreatedAt         DateTime64(3, 'UTC')   DEFAULT now64(3) COMMENT 'Row insert time (UTC)'                                    CODEC(DoubleDelta, ZSTD(1)),

    INDEX idx_run_time   RunTime   TYPE minmax              GRANULARITY 1,
    INDEX idx_cause_kind CauseKind TYPE set(16)             GRANULARITY 1,
    INDEX idx_lift       Lift      TYPE minmax              GRANULARITY 1
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(RunTime)
ORDER BY (ProjectId, FunnelId, RunTime, StepIndex, CauseKind, CauseKey)
SETTINGS index_granularity = 8192;
