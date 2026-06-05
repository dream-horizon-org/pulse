-- Local single-node ClickHouse (no ON CLUSTER). Safe to re-run.
-- Bridge + attribution tables for funnel drop-off correlation and RCA.
-- Source: backend/db/dev/clickhouse/17–19_otel.funnel_*.sql

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

CREATE TABLE IF NOT EXISTS otel.funnel_user_state
(
    FunnelId                  UInt64                 COMMENT 'MySQL funnel.id'                                            CODEC(T64, ZSTD(1)),
    ProjectId                 LowCardinality(String) COMMENT 'Project ID (proj-xxx)'                                       CODEC(ZSTD(1)),
    RunTime                   DateTime64(3, 'UTC')   COMMENT 'Job run time (matches funnel_results.RunTime)'              CODEC(DoubleDelta, ZSTD(1)),
    UserId                    String                 COMMENT 'User identity (AppInstallationId in OTel terms)'            CODEC(ZSTD(1)),
    MaxReachedStep            UInt8                  COMMENT 'Furthest step reached by this user across all sessions'     CODEC(T64, ZSTD(1)),
    DropoffStep               Int8                   COMMENT '-1 if any session converted; else MaxReachedStep + 1'       CODEC(T64, ZSTD(1)),
    CanonicalSessionId        String                 COMMENT 'Session anchor for OTel correlation (furthest step wins)'   CODEC(ZSTD(1)),
    CanonicalLastReachedAt    DateTime64(3, 'UTC')                                                                        CODEC(DoubleDelta, ZSTD(1)),
    CanonicalTraceIdAtDropoff String                                                                                       CODEC(ZSTD(1)),
    CanonicalScreenAtDropoff  LowCardinality(String)                                                                       CODEC(ZSTD(1)),
    AppVersion                LowCardinality(String)                                                                       CODEC(ZSTD(1)),
    OsName                    LowCardinality(String)                                                                       CODEC(ZSTD(1)),
    OsVersion                 LowCardinality(String)                                                                       CODEC(ZSTD(1)),
    Platform                  LowCardinality(String)                                                                       CODEC(ZSTD(1)),
    DeviceModel               LowCardinality(String)                                                                       CODEC(ZSTD(1)),
    NetworkProvider           LowCardinality(String)                                                                       CODEC(ZSTD(1)),
    GeoCountry                LowCardinality(String)                                                                       CODEC(ZSTD(1)),
    SessionAttempts           UInt32                 COMMENT 'How many of this user''s sessions touched the funnel'        CODEC(T64, ZSTD(1)),
    CreatedAt                 DateTime64(3, 'UTC')   DEFAULT now64(3) COMMENT 'Row insert time (UTC)'                      CODEC(DoubleDelta, ZSTD(1)),

    INDEX idx_user_id        UserId             TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_canonical_sess CanonicalSessionId TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_run_time       RunTime            TYPE minmax             GRANULARITY 1,
    INDEX idx_dropoff_step   DropoffStep        TYPE set(32)            GRANULARITY 1
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(RunTime)
ORDER BY (ProjectId, FunnelId, RunTime, DropoffStep, UserId)
SETTINGS index_granularity = 8192;

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
