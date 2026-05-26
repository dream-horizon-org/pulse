CREATE TABLE IF NOT EXISTS otel.funnel_user_state_local
ON CLUSTER 'pulse-ch'
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
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/funnel_user_state_local', '{replica}')
PARTITION BY toYYYYMM(RunTime)
ORDER BY (ProjectId, FunnelId, RunTime, DropoffStep, UserId)
TTL toDateTime(RunTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(RunTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';


CREATE TABLE IF NOT EXISTS otel.funnel_user_state
ON CLUSTER 'pulse-ch'
AS otel.funnel_user_state_local
ENGINE = Distributed('pulse-ch', otel, funnel_user_state_local, cityHash64((ProjectId, FunnelId)));
