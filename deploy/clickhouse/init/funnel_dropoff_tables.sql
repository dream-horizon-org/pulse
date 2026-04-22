-- =====================================================================
-- Funnel drop-off correlation tables
-- =====================================================================
-- Adds three ClickHouse tables used to correlate funnel step drop-offs
-- with OTel reliability/performance signals (crashes, ANRs, HTTP errors,
-- frozen frames, session replay) and surface ranked causes with baseline
-- lift vs converter cohort.
--
-- Tables:
--   1. funnel_session_state_local          — always per-session bridge.
--   2. funnel_user_state_local             — user-level rollup
--                                             (used only when a funnel's
--                                             `mode` is UNIQUE_USERS).
--   3. funnel_dropoff_attribution_local    — precomputed (step × cause)
--                                             causes with lift & examples.
--
-- Conventions align with otel.funnel_results_local / journey_results_local.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. funnel_session_state — per-(funnel × session) bridge
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS otel.funnel_session_state_local
ON CLUSTER `pulse-clickhouse`
(
    `FunnelId`             UInt64                 CODEC(T64, ZSTD(1))         COMMENT 'MySQL funnel.id',
    `ProjectId`            LowCardinality(String) CODEC(ZSTD(1))              COMMENT 'Project ID',
    `RunTime`              DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)) COMMENT 'Spark job execution time (matches funnel_results.RunTime)',
    `SessionId`            String                 CODEC(ZSTD(1))              COMMENT 'OTel SessionId — the join key to all OTel tables',
    `UserId`               String                 CODEC(ZSTD(1))              COMMENT 'User at the time of the furthest step (may be empty for anonymous)',
    `LastReachedStep`      UInt8                  CODEC(T64, ZSTD(1))         COMMENT '0-based index of the furthest step reached by this session',
    `LastReachedStepName`  LowCardinality(String) CODEC(ZSTD(1))              COMMENT 'Event name for LastReachedStep',
    `LastReachedAt`        DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)) COMMENT 'Timestamp of the LastReachedStep event — anchor for OTel window joins',
    `DropoffStep`          Int8                   CODEC(T64, ZSTD(1))         COMMENT '-1 if this session converted (reached final step), else LastReachedStep + 1',
    `TimeToDropoffSec`     Int64                  CODEC(T64, ZSTD(1))         COMMENT 'Seconds from step 0 to LastReachedAt; NULL-equivalent (-1) for step-0 only sessions',
    `ScreenAtDropoff`      LowCardinality(String) CODEC(ZSTD(1))              COMMENT 'Screen name active at LastReachedAt, for heatmap drill-in',
    `TraceIdAtDropoff`     String                 CODEC(ZSTD(1))              COMMENT 'Trace ID of the LastReachedStep event, for waterfall drill-in',
    `AppVersion`           LowCardinality(String) CODEC(ZSTD(1)),
    `OsName`               LowCardinality(String) CODEC(ZSTD(1)),
    `OsVersion`            LowCardinality(String) CODEC(ZSTD(1)),
    `Platform`             LowCardinality(String) CODEC(ZSTD(1)),
    `DeviceModel`          LowCardinality(String) CODEC(ZSTD(1)),
    `NetworkProvider`      LowCardinality(String) CODEC(ZSTD(1)),
    `GeoCountry`           LowCardinality(String) CODEC(ZSTD(1)),
    `CreatedAt`            DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)) DEFAULT now64(3),

    INDEX idx_session_id   SessionId   TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_user_id      UserId      TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_run_time     RunTime     TYPE minmax             GRANULARITY 1,
    INDEX idx_dropoff_step DropoffStep TYPE set(32)            GRANULARITY 1,
    INDEX idx_app_version  AppVersion  TYPE bloom_filter(0.01) GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/funnel_session_state_local', '{replica}')
PARTITION BY toYYYYMM(RunTime)
ORDER BY (ProjectId, FunnelId, RunTime, DropoffStep, SessionId)
TTL toDateTime(RunTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(RunTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.funnel_session_state
ON CLUSTER `pulse-clickhouse`
AS otel.funnel_session_state_local
ENGINE = Distributed(`pulse-clickhouse`, otel, funnel_session_state_local, cityHash64((ProjectId, FunnelId)));


-- ---------------------------------------------------------------------
-- 2. funnel_user_state — per-(funnel × user) rollup
-- ---------------------------------------------------------------------
-- Populated only for funnels whose mode = 'UNIQUE_USERS'. Each row picks
-- the user's canonical session (the one that reached MaxReachedStep, most
-- recent on ties) so OTel correlation has a single session anchor.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS otel.funnel_user_state_local
ON CLUSTER `pulse-clickhouse`
(
    `FunnelId`                   UInt64                 CODEC(T64, ZSTD(1)),
    `ProjectId`                  LowCardinality(String) CODEC(ZSTD(1)),
    `RunTime`                    DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)),
    `UserId`                     String                 CODEC(ZSTD(1)),
    `MaxReachedStep`             UInt8                  CODEC(T64, ZSTD(1))         COMMENT 'max(LastReachedStep) across all of this user''s sessions in the run window',
    `DropoffStep`                Int8                   CODEC(T64, ZSTD(1))         COMMENT '-1 if any session converted; else MaxReachedStep + 1',
    `CanonicalSessionId`         String                 CODEC(ZSTD(1))              COMMENT 'Chosen session to anchor OTel correlation (furthest step, most recent)',
    `CanonicalLastReachedAt`     DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)),
    `CanonicalTraceIdAtDropoff`  String                 CODEC(ZSTD(1)),
    `CanonicalScreenAtDropoff`   LowCardinality(String) CODEC(ZSTD(1)),
    `AppVersion`                 LowCardinality(String) CODEC(ZSTD(1)),
    `OsName`                     LowCardinality(String) CODEC(ZSTD(1)),
    `OsVersion`                  LowCardinality(String) CODEC(ZSTD(1)),
    `Platform`                   LowCardinality(String) CODEC(ZSTD(1)),
    `DeviceModel`                LowCardinality(String) CODEC(ZSTD(1)),
    `NetworkProvider`            LowCardinality(String) CODEC(ZSTD(1)),
    `GeoCountry`                 LowCardinality(String) CODEC(ZSTD(1)),
    `SessionAttempts`            UInt32                 CODEC(T64, ZSTD(1))         COMMENT 'How many of this user''s sessions entered the funnel',
    `CreatedAt`                  DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)) DEFAULT now64(3),

    INDEX idx_user_id        UserId              TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_canonical_sess CanonicalSessionId  TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_run_time       RunTime             TYPE minmax             GRANULARITY 1,
    INDEX idx_dropoff_step   DropoffStep         TYPE set(32)            GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/funnel_user_state_local', '{replica}')
PARTITION BY toYYYYMM(RunTime)
ORDER BY (ProjectId, FunnelId, RunTime, DropoffStep, UserId)
TTL toDateTime(RunTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(RunTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.funnel_user_state
ON CLUSTER `pulse-clickhouse`
AS otel.funnel_user_state_local
ENGINE = Distributed(`pulse-clickhouse`, otel, funnel_user_state_local, cityHash64((ProjectId, FunnelId)));


-- ---------------------------------------------------------------------
-- 3. funnel_dropoff_attribution — precomputed (step × cause) rankings
-- ---------------------------------------------------------------------
-- One row per (FunnelId, RunTime, StepIndex, CauseKind, CauseKey).
-- The side-panel renders these rows directly — ranked by Lift DESC.
-- ExampleSessions is a capped array of canonical session IDs to support
-- evidence drill-in without another cohort scan.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS otel.funnel_dropoff_attribution_local
ON CLUSTER `pulse-clickhouse`
(
    `FunnelId`           UInt64                 CODEC(T64, ZSTD(1)),
    `ProjectId`          LowCardinality(String) CODEC(ZSTD(1)),
    `RunTime`            DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)),
    `StepIndex`          UInt8                  CODEC(T64, ZSTD(1))   COMMENT 'Drop-off FROM this step (i.e. users reached StepIndex, failed to reach StepIndex+1)',
    `CauseKind`          LowCardinality(String) CODEC(ZSTD(1))        COMMENT 'crash | anr | non_fatal | http_5xx | http_4xx | frozen_frame | slow_interaction | rage_tap | dead_click | network_offline',
    `CauseKey`           String                 CODEC(ZSTD(1))        COMMENT 'E.g. stack_trace GroupId, http host+status, ScreenName — human-readable',
    `CauseLabel`         String                 CODEC(ZSTD(3))        COMMENT 'Human display label (UI never needs to compose one)',
    `DropoffCohort`      UInt64                 CODEC(T64, ZSTD(1))   COMMENT 'Total droppers at this step (sessions for SESSIONS mode, users for UNIQUE_USERS)',
    `DropoffAffected`    UInt64                 CODEC(T64, ZSTD(1))   COMMENT 'Droppers who hit this cause in the dropoff window',
    `ConverterCohort`    UInt64                 CODEC(T64, ZSTD(1))   COMMENT 'Total converters (reached final step) — baseline denominator',
    `ConverterAffected`  UInt64                 CODEC(T64, ZSTD(1))   COMMENT 'Converters who hit this cause in the equivalent window',
    `Lift`               Float64                CODEC(ZSTD(1))        COMMENT '(DropoffAffected/DropoffCohort) / (ConverterAffected/ConverterCohort)',
    `PValue`             Float64                CODEC(ZSTD(1))        COMMENT 'Chi-square / Fisher p-value for (cause, cohort) independence',
    `ExampleSessions`    Array(String)          CODEC(ZSTD(3))        COMMENT 'Up to 50 canonical session IDs for evidence drill-in',
    `CreatedAt`          DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)) DEFAULT now64(3),

    INDEX idx_run_time      RunTime   TYPE minmax             GRANULARITY 1,
    INDEX idx_cause_kind    CauseKind TYPE set(16)            GRANULARITY 1,
    INDEX idx_lift          Lift      TYPE minmax             GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/funnel_dropoff_attribution_local', '{replica}')
PARTITION BY toYYYYMM(RunTime)
ORDER BY (ProjectId, FunnelId, RunTime, StepIndex, CauseKind, CauseKey)
TTL toDateTime(RunTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(RunTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.funnel_dropoff_attribution
ON CLUSTER `pulse-clickhouse`
AS otel.funnel_dropoff_attribution_local
ENGINE = Distributed(`pulse-clickhouse`, otel, funnel_dropoff_attribution_local, cityHash64((ProjectId, FunnelId)));

-- =====================================================================
-- END
-- =====================================================================
