-- =====================================================================
-- pulse-clickhouse cluster migration — consolidated DDL
-- =====================================================================
-- Covers 10 tables optimized during the migration review session.
-- Conventions applied session-wide:
--   • ON CLUSTER 'pulse-clickhouse' on every DDL
--   • Replicated* engines with zk path /clickhouse/tables/{shard}/<db>/<table>
--   • TTL default: 7d TO VOLUME 'cold', 90d DELETE (overridden per table where noted)
--   • Codecs: DoubleDelta on monotonic DateTime64, T64 on bounded ints,
--             ZSTD(3) on Maps/JSON/repetitive text, ZSTD(1) on LC/IDs
--   • MATERIALIZED columns carry explicit codecs
--   • Distributed tables shard by the grain that guarantees
--     engine-correctness (ReplacingMergeTree / AggregatingMergeTree need
--     logical-key convergence on one shard)
--
-- ⚠️  VERIFY — otel.otel_logs_local and otel.otel_traces_local sections
--     below are reconstructed from session memory. Confirm against the
--     final DDLs locked earlier in this conversation before applying.
-- =====================================================================


-- =====================================================================
-- 1. otel.otel_logs_local  (⚠️ verify against earlier locked version)
-- =====================================================================
CREATE TABLE otel.otel_logs_local
    ON CLUSTER 'pulse-clickhouse'
(
    `Timestamp`           DateTime64(9)                         CODEC(DoubleDelta, ZSTD(1)),
    `TraceId`             String                                CODEC(ZSTD(3)),
    `SpanId`              FixedString(16)                       CODEC(ZSTD(1)),
    `TraceFlags`          UInt8                                 CODEC(T64, ZSTD(1)),
    `SeverityText`        LowCardinality(String)                CODEC(ZSTD(1)),
    `SeverityNumber`      Int8                                  CODEC(T64, ZSTD(1)),
    `ServiceName`         LowCardinality(String)                CODEC(ZSTD(1)),
    `Body`                String                                CODEC(ZSTD(3)),
    `ResourceSchemaUrl`   LowCardinality(String)                CODEC(ZSTD(1)),
    `ResourceAttributes`  Map(LowCardinality(String), String)   CODEC(ZSTD(3)),
    `ScopeSchemaUrl`      LowCardinality(String)                CODEC(ZSTD(1)),
    `ScopeName`           LowCardinality(String)                CODEC(ZSTD(1)),
    `ScopeVersion`        LowCardinality(String)                CODEC(ZSTD(1)),
    `ScopeAttributes`     Map(LowCardinality(String), String)   CODEC(ZSTD(3)),
    `LogAttributes`       Map(LowCardinality(String), String)   CODEC(ZSTD(3)),

    `SessionId`           String                  MATERIALIZED ifNull(LogAttributes['session.id'], '')                                                                 CODEC(ZSTD(1)),
    `MeteringSessionId`   String                  MATERIALIZED ifNull(LogAttributes['pulse.metering.session.id'], '')                                                  CODEC(ZSTD(1)),
    `ProjectId`           LowCardinality(String)  MATERIALIZED ifNull(ResourceAttributes['project.id'], '')                                                            CODEC(ZSTD(1)),
    `AppVersion`          LowCardinality(String)  MATERIALIZED ifNull(ResourceAttributes['app.build_name'], '')                                                        CODEC(ZSTD(1)),
    `SDKVersion`          LowCardinality(String)  MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], '')                                                       CODEC(ZSTD(1)),
    `Platform`            LowCardinality(String)  MATERIALIZED ifNull(ResourceAttributes['os.name'], '')                                                               CODEC(ZSTD(1)),
    `OsVersion`           LowCardinality(String)  MATERIALIZED ifNull(ResourceAttributes['os.version'], '')                                                            CODEC(ZSTD(1)),
    `GeoState`            LowCardinality(String)  MATERIALIZED ifNull(LogAttributes['geo.region.iso_code'], '')                                                        CODEC(ZSTD(1)),
    `GeoCountry`          LowCardinality(String)  MATERIALIZED ifNull(LogAttributes['geo.country.iso_code'], '')                                                       CODEC(ZSTD(1)),
    `DeviceModel`         LowCardinality(String)  MATERIALIZED ifNull(ResourceAttributes['device.model.name'], '')                                                     CODEC(ZSTD(1)),
    `NetworkProvider`     LowCardinality(String)  MATERIALIZED ifNull(LogAttributes['network.carrier.name'], '')                                                       CODEC(ZSTD(1)),
    `UserId`              String                  MATERIALIZED ifNull(LogAttributes['user.id'], '')          CODEC(ZSTD(1)),
    `AppIntstallationId`  String                  MATERIALIZED ifNull(LogAttributes['app.installation.id'], '')          CODEC(ZSTD(1)),
    `PulseType`           LowCardinality(String)  MATERIALIZED ifNull(LogAttributes['pulse.type'], 'otel')                                                             CODEC(ZSTD(1)),

    `EventName`           LowCardinality(String)                CODEC(ZSTD(1)),

    INDEX idx_trace_id      TraceId        TYPE bloom_filter(0.001) GRANULARITY 1,
    INDEX idx_session_id    SessionId      TYPE bloom_filter(0.001) GRANULARITY 1,
    INDEX idx_user_id       UserId         TYPE bloom_filter(0.001) GRANULARITY 1,
    INDEX idx_span_id       SpanId         TYPE bloom_filter(0.001) GRANULARITY 4,
    INDEX idx_severity_num  SeverityNumber TYPE set(32)              GRANULARITY 1
    )
    ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/otel_logs_local', '{replica}')
    PARTITION BY toYYYYMMDD(Timestamp)
    ORDER BY (ProjectId, PulseType, EventName, Timestamp)
    TTL
    toDateTime(Timestamp) + INTERVAL 7  DAY TO VOLUME 'cold',
    toDateTime(Timestamp) + INTERVAL 90 DAY DELETE
SETTINGS
    index_granularity  = 8192,
    storage_policy     = 'tiered';

CREATE TABLE IF NOT EXISTS otel.otel_logs
    ON CLUSTER `pulse-clickhouse`
AS otel.otel_logs_local
    ENGINE = Distributed(`pulse-clickhouse`, otel, otel_logs_local, cityHash64(TraceId));


-- =====================================================================
-- 2. otel.otel_traces_local  (⚠️ verify against earlier locked version)
-- =====================================================================
CREATE TABLE IF NOT EXISTS otel.otel_traces_local
    ON CLUSTER 'pulse-clickhouse'
(
    Timestamp          DateTime64(9, 'UTC')                              CODEC(DoubleDelta, ZSTD(1)),
    TraceId            String                                            CODEC(ZSTD(1)),
    SpanId             FixedString(16)                                   CODEC(ZSTD(1)),
    ParentSpanId       FixedString(16)                                   CODEC(ZSTD(1)),
    TraceState         String                                            CODEC(ZSTD(3)),
    SpanName           LowCardinality(String)                            CODEC(ZSTD(1)),
    SpanKind           LowCardinality(String)                            CODEC(ZSTD(1)),
    ServiceName        LowCardinality(String)                            CODEC(ZSTD(1)),
    ResourceAttributes Map(LowCardinality(String), String)               CODEC(ZSTD(3)),
    ScopeName          LowCardinality(String)                            CODEC(ZSTD(1)),
    ScopeVersion       LowCardinality(String)                            CODEC(ZSTD(1)),
    SpanAttributes     Map(LowCardinality(String), String)               CODEC(ZSTD(3)),
    Duration           Int64                                             CODEC(T64, ZSTD(1)),
    StatusCode         LowCardinality(String)                            CODEC(ZSTD(1)),
    StatusMessage      String                                            CODEC(ZSTD(3)),

    `Events.Timestamp`  Array(DateTime64(9, 'UTC'))                      CODEC(DoubleDelta, ZSTD(1)),
    `Events.Name`       Array(LowCardinality(String))                    CODEC(ZSTD(1)),
    `Events.Attributes` Array(Map(LowCardinality(String), String))       CODEC(ZSTD(3)),

    `Links.TraceId`     Array(String)                                    CODEC(ZSTD(1)),
    `Links.SpanId`      Array(String)                                    CODEC(ZSTD(1)),
    `Links.TraceState`  Array(String)                                    CODEC(ZSTD(3)),
    `Links.Attributes`  Array(Map(LowCardinality(String), String))       CODEC(ZSTD(3)),

    ProjectId          LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], '')              CODEC(ZSTD(1)),
    SpanType           LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['pulse.type'], '')                  CODEC(ZSTD(1)),
    PulseType          LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['pulse.type'], '')                  CODEC(ZSTD(1)),
    SessionId          String                 MATERIALIZED ifNull(SpanAttributes['session.id'], '')                  CODEC(ZSTD(1)),
    AppVersion         LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['app.version'], '')             CODEC(ZSTD(1)),
    SDKVersion         LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['telemetry.sdk.version'], '')   CODEC(ZSTD(1)),
    Platform           LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.type'], '')                 CODEC(ZSTD(1)),
    OsVersion          LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], '')              CODEC(ZSTD(1)),
    GeoState           LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['geo.state'], '')                   CODEC(ZSTD(1)),
    GeoCountry         LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['geo.country'], '')                 CODEC(ZSTD(1)),
    DeviceModel        LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.identifier'], '') CODEC(ZSTD(1)),
    NetworkProvider    LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['network.carrier.name'], '')        CODEC(ZSTD(1)),
    MeteringSessionId  String                 MATERIALIZED ifNull(SpanAttributes['metering.session.id'], '')         CODEC(ZSTD(1)),
    UserId             String                 MATERIALIZED ifNull(SpanAttributes['user.id'], '')                     CODEC(ZSTD(1)),

    INDEX idx_trace_id      TraceId           TYPE bloom_filter(0.001) GRANULARITY 1,
    INDEX idx_span_id       SpanId            TYPE bloom_filter(0.01)  GRANULARITY 1,
    INDEX idx_parent_span   ParentSpanId      TYPE bloom_filter(0.01)  GRANULARITY 1,
    INDEX idx_session_id    SessionId         TYPE bloom_filter(0.01)  GRANULARITY 1,
    INDEX idx_user          UserId            TYPE bloom_filter(0.01)  GRANULARITY 1,
    INDEX idx_metering_sid  MeteringSessionId TYPE bloom_filter(0.01)  GRANULARITY 1,
    INDEX idx_span_name     SpanName          TYPE bloom_filter(0.01)  GRANULARITY 4,
    INDEX idx_status        StatusCode        TYPE set(8)              GRANULARITY 1,
    INDEX idx_kind          SpanKind          TYPE set(8)              GRANULARITY 1,
    INDEX idx_duration      Duration          TYPE minmax              GRANULARITY 1,
    INDEX idx_ts            Timestamp         TYPE minmax              GRANULARITY 1
    )
    ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/otel_traces_local', '{replica}')
    PARTITION BY toYYYYMMDD(Timestamp)
    ORDER BY (ProjectId, PulseType, SpanName, Timestamp)
    TTL toDateTime(Timestamp) + INTERVAL 7  DAY TO VOLUME 'cold',
    toDateTime(Timestamp) + INTERVAL 90 DAY DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.otel_traces
ON CLUSTER 'pulse-clickhouse'
AS otel.otel_traces_local
ENGINE = Distributed('pulse-clickhouse', otel, otel_traces_local, cityHash64(TraceId));

-- Optional follow-up (NOT applied): network-span hot columns.
-- Apply separately if you proceed with the network-query optimization:
--   ALTER TABLE otel.otel_traces_local ON CLUSTER 'pulse-clickhouse'
--     ADD COLUMN HttpUrl        String                 MATERIALIZED ifNull(SpanAttributes['http.url'], ifNull(SpanAttributes['url.full'], '')) CODEC(ZSTD(3)),
--     ADD COLUMN HttpHost       LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['net.peer.name'], ifNull(SpanAttributes['server.address'], '')) CODEC(ZSTD(1)),
--     ADD COLUMN HttpMethod     LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['http.method'], ifNull(SpanAttributes['http.request.method'], '')) CODEC(ZSTD(1)),
--     ADD COLUMN HttpStatusCode UInt16                 MATERIALIZED toUInt16OrZero(ifNull(SpanAttributes['http.status_code'], ifNull(SpanAttributes['http.response.status_code'], '0'))) CODEC(T64, ZSTD(1)),
--     ADD INDEX  idx_http_host    HttpHost       TYPE bloom_filter(0.01) GRANULARITY 1,
--     ADD INDEX  idx_http_method  HttpMethod     TYPE set(16)            GRANULARITY 1,
--     ADD INDEX  idx_http_status  HttpStatusCode TYPE minmax             GRANULARITY 1;


-- =====================================================================
-- 3. otel.stack_trace_events_local
-- =====================================================================
CREATE TABLE otel.stack_trace_events_local
ON CLUSTER 'pulse-clickhouse'
(
    Timestamp               DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)) COMMENT 'event time (ns precision, store UTC)',
    EventName               LowCardinality(String) CODEC(ZSTD(1)),
    Title                   String                 CODEC(ZSTD(3)),
    ExceptionStackTrace     String                 CODEC(ZSTD(3)),
    ExceptionStackTraceRaw  String                 CODEC(ZSTD(3)),
    ExceptionMessage        String                 CODEC(ZSTD(3)),
    ExceptionType           LowCardinality(String) CODEC(ZSTD(1)),
    Interactions            Array(LowCardinality(String)) CODEC(ZSTD(1)),
    ScreenName              LowCardinality(String) CODEC(ZSTD(1)),
    UserId                  String                 CODEC(ZSTD(1)),
    SessionId               String                 CODEC(ZSTD(1)),
    Platform                LowCardinality(String) CODEC(ZSTD(1)),
    OsVersion               LowCardinality(String) CODEC(ZSTD(1)),
    DeviceModel             LowCardinality(String) CODEC(ZSTD(1)),
    AppVersionCode          LowCardinality(String) CODEC(ZSTD(1)),
    AppVersion              LowCardinality(String) CODEC(ZSTD(1)),
    SdkVersion              LowCardinality(String) CODEC(ZSTD(1)),
    BundleId                String                 CODEC(ZSTD(1)),
    TraceId                 String                 CODEC(ZSTD(1)),
    SpanId                  FixedString(16)        CODEC(ZSTD(1)),
    GroupId                 String                 CODEC(ZSTD(1)),
    Signature               String                 CODEC(ZSTD(1)),
    Fingerprint             String                 CODEC(ZSTD(1)),
    ScopeAttributes         Map(LowCardinality(String), String) CODEC(ZSTD(3)),
    LogAttributes           Map(LowCardinality(String), String) CODEC(ZSTD(3)),
    ResourceAttributes      Map(LowCardinality(String), String) CODEC(ZSTD(3)),
    ProjectId               LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], '')           CODEC(ZSTD(1)),
    PulseType               LowCardinality(String) MATERIALIZED ifNull(LogAttributes['pulse.type'], 'otel')            CODEC(ZSTD(1)),
    MeteringSessionId       String                 MATERIALIZED ifNull(LogAttributes['pulse.metering.session.id'], '') CODEC(ZSTD(1)),

    INDEX idx_session_id       SessionId         TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_group_id         GroupId           TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_fingerprint      Fingerprint       TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_user_id          UserId            TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_metering_session MeteringSessionId TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_span_id          SpanId            TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_exception_type   ExceptionType     TYPE set(256)           GRANULARITY 1,
    INDEX idx_pulse_type       PulseType         TYPE set(16)            GRANULARITY 1,
    INDEX idx_platform         Platform          TYPE set(8)             GRANULARITY 1,
    INDEX idx_os_version       OsVersion         TYPE set(256)           GRANULARITY 1,
    INDEX idx_timestamp        Timestamp         TYPE minmax             GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/stack_trace_events_local', '{replica}')
PARTITION BY toYYYYMMDD(Timestamp)
ORDER BY (ProjectId, GroupId, ExceptionType, Timestamp)
TTL toDateTime(Timestamp) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(Timestamp) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.stack_trace_events
ON CLUSTER 'pulse-clickhouse'
AS otel.stack_trace_events_local
ENGINE = Distributed('pulse-clickhouse', otel, stack_trace_events_local, cityHash64(GroupId));


-- =====================================================================
-- 4. otel.session_summary
-- =====================================================================
CREATE TABLE otel.session_summary
ON CLUSTER 'pulse-clickhouse'
(
    ProjectId            LowCardinality(String)                                      CODEC(ZSTD(1)),
    sessionId            String                                                      CODEC(ZSTD(1)),
    startTime            SimpleAggregateFunction(min, DateTime64(9, 'UTC'))          CODEC(DoubleDelta, ZSTD(1)),
    endTime              SimpleAggregateFunction(max, DateTime64(9, 'UTC'))          CODEC(DoubleDelta, ZSTD(1)),
    userId               SimpleAggregateFunction(any, String)                        CODEC(ZSTD(1)),
    platform             SimpleAggregateFunction(any, LowCardinality(String))        CODEC(ZSTD(1)),
    appVersion           SimpleAggregateFunction(any, LowCardinality(String))        CODEC(ZSTD(1)),
    osVersion            SimpleAggregateFunction(any, LowCardinality(String))        CODEC(ZSTD(1)),
    deviceModel          SimpleAggregateFunction(any, LowCardinality(String))        CODEC(ZSTD(1)),
    networkProvider      SimpleAggregateFunction(any, LowCardinality(String))        CODEC(ZSTD(1)),
    geoCountry           SimpleAggregateFunction(any, LowCardinality(String))        CODEC(ZSTD(1)),
    geoRegion            SimpleAggregateFunction(any, LowCardinality(String))        CODEC(ZSTD(1)),
    apdexSum             SimpleAggregateFunction(sum, Float64)                       CODEC(ZSTD(1)),
    apdexCount           SimpleAggregateFunction(sum, UInt64)                        CODEC(T64, ZSTD(1)),
    networkErrors        SimpleAggregateFunction(sum, UInt64)                        CODEC(T64, ZSTD(1)),
    interactionErrors    SimpleAggregateFunction(sum, UInt64)                        CODEC(T64, ZSTD(1)),
    crashCount           SimpleAggregateFunction(sum, UInt64)                        CODEC(T64, ZSTD(1)),
    anrCount             SimpleAggregateFunction(sum, UInt64)                        CODEC(T64, ZSTD(1)),
    nonFatal             SimpleAggregateFunction(sum, UInt64)                        CODEC(T64, ZSTD(1)),
    slowInteractionCount SimpleAggregateFunction(sum, UInt64)                        CODEC(T64, ZSTD(1)),
    frozenFrameCount     SimpleAggregateFunction(sum, Float64)                       CODEC(ZSTD(1)),
    spanCount            SimpleAggregateFunction(sum, UInt64)                        CODEC(T64, ZSTD(1)),

    INDEX idx_user_id     userId      TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_start_time  startTime   TYPE minmax             GRANULARITY 1,
    INDEX idx_end_time    endTime     TYPE minmax             GRANULARITY 1,
    INDEX idx_crash_count crashCount  TYPE minmax             GRANULARITY 1,
    INDEX idx_anr_count   anrCount    TYPE minmax             GRANULARITY 1,
    INDEX idx_nonfatal    nonFatal    TYPE minmax             GRANULARITY 1
)
ENGINE = ReplicatedAggregatingMergeTree('/clickhouse/tables/{shard}/otel/session_summary', '{replica}')
PARTITION BY (ProjectId, toYYYYMMDD(startTime))
ORDER BY (ProjectId, sessionId)
TTL toDateTime(startTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(startTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.session_summary_distributed
ON CLUSTER 'pulse-clickhouse'
AS otel.session_summary
ENGINE = Distributed('pulse-clickhouse', otel, session_summary, cityHash64(sessionId));


-- =====================================================================
-- 5. otel.session_replay_events
-- =====================================================================
CREATE TABLE otel.session_replay_events
ON CLUSTER 'pulse-clickhouse'
(
    SessionId            String                 CODEC(ZSTD(1)),
    ProjectId            LowCardinality(String) CODEC(ZSTD(1)),
    UserId               String                 CODEC(ZSTD(1)),
    MinFirstTimestamp    SimpleAggregateFunction(min, DateTime64(6, 'UTC'))                       CODEC(DoubleDelta, ZSTD(1)),
    MaxLastTimestamp     SimpleAggregateFunction(max, DateTime64(6, 'UTC'))                       CODEC(DoubleDelta, ZSTD(1)),
    BlockUrls            SimpleAggregateFunction(groupArrayArray, Array(String))                  CODEC(ZSTD(3)),
    BlockFirstTimestamps SimpleAggregateFunction(groupArrayArray, Array(DateTime64(6, 'UTC')))    CODEC(ZSTD(3)),
    BlockLastTimestamps  SimpleAggregateFunction(groupArrayArray, Array(DateTime64(6, 'UTC')))    CODEC(ZSTD(3)),
    SnapshotSource       AggregateFunction(argMin, LowCardinality(String), DateTime64(6, 'UTC')) CODEC(ZSTD(1)),

    INDEX idx_user_id      UserId            TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_min_first_ts MinFirstTimestamp TYPE minmax             GRANULARITY 1,
    INDEX idx_max_last_ts  MaxLastTimestamp  TYPE minmax             GRANULARITY 1
)
ENGINE = ReplicatedAggregatingMergeTree('/clickhouse/tables/{shard}/otel/session_replay_events', '{replica}')
PARTITION BY (ProjectId, toYYYYMMDD(MinFirstTimestamp))
ORDER BY (ProjectId, SessionId)
TTL toDateTime(MinFirstTimestamp) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(MaxLastTimestamp)  + toIntervalDay(90) DELETE
SETTINGS merge_with_ttl_timeout = 86400, index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.session_replay_events_distributed
ON CLUSTER 'pulse-clickhouse'
AS otel.session_replay_events
ENGINE = Distributed('pulse-clickhouse', otel, session_replay_events, cityHash64(SessionId));


-- =====================================================================
-- 6. otel.root_cause_cache
-- =====================================================================
CREATE TABLE otel.root_cause_cache
ON CLUSTER 'pulse-clickhouse'
(
    ProjectId         LowCardinality(String) CODEC(ZSTD(1)),
    interaction_name  LowCardinality(String) CODEC(ZSTD(1)),
    date              Date                   CODEC(Delta, ZSTD(1)),
    window_end_utc    DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)) COMMENT 'Exclusive upper bound of RCA query window',
    mode              LowCardinality(String) CODEC(ZSTD(1))              COMMENT 'hierarchical | flat',
    baseline          String                 CODEC(ZSTD(3))              COMMENT 'JSON',
    segments          String                 CODEC(ZSTD(3))              COMMENT 'JSON',
    cached_at         DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)),

    INDEX idx_window_end window_end_utc TYPE minmax GRANULARITY 1,
    INDEX idx_cached_at  cached_at      TYPE minmax GRANULARITY 1,
    INDEX idx_mode       mode           TYPE set(4) GRANULARITY 1
)
ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{shard}/otel/root_cause_cache', '{replica}', cached_at)
PARTITION BY toYYYYMM(date)
ORDER BY (ProjectId, interaction_name, date, mode)
TTL toDateTime(date) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(date) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.root_cause_cache_distributed
ON CLUSTER 'pulse-clickhouse'
AS otel.root_cause_cache
ENGINE = Distributed('pulse-clickhouse', otel, root_cause_cache, cityHash64((ProjectId, interaction_name)));


-- =====================================================================
-- 7. otel.project_monthly_usage
--    TTL: 30d TO cold, NO DELETE (billing retention — long-lived audit)
-- =====================================================================
CREATE TABLE otel.project_monthly_usage
ON CLUSTER 'pulse-clickhouse'
(
    project_id    String                                     CODEC(ZSTD(1)),
    month         Date                                       CODEC(Delta, ZSTD(1)),
    source        LowCardinality(String)                     CODEC(ZSTD(1)),
    event_count   SimpleAggregateFunction(sum, UInt64)       CODEC(T64, ZSTD(1)),
    session_count AggregateFunction(uniqCombined64, String)  CODEC(ZSTD(1))
)
ENGINE = ReplicatedAggregatingMergeTree('/clickhouse/tables/{shard}/otel/project_monthly_usage', '{replica}')
PARTITION BY toYYYYMM(month)
ORDER BY (project_id, month, source)
TTL toDateTime(month) + toIntervalDay(30) TO VOLUME 'cold'
SETTINGS index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.project_monthly_usage_distributed
ON CLUSTER 'pulse-clickhouse'
AS otel.project_monthly_usage
ENGINE = Distributed('pulse-clickhouse', otel, project_monthly_usage, cityHash64(project_id));


-- =====================================================================
-- 8. otel.journey_results_local
-- =====================================================================
CREATE TABLE otel.journey_results_local
ON CLUSTER 'pulse-clickhouse'
(
    JourneyId  UInt64                 CODEC(T64, ZSTD(1))         COMMENT 'MySQL journey.id',
    ProjectId  LowCardinality(String) CODEC(ZSTD(1))              COMMENT 'Project ID',
    RunTime    DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)) COMMENT 'Spark job execution time (UTC)',
    Direction  LowCardinality(String) CODEC(ZSTD(1))              COMMENT 'START | END',
    PosFrom    Int32                  CODEC(T64, ZSTD(1))         COMMENT 'Source path position; -1 = ENTRY',
    EventFrom  LowCardinality(String) CODEC(ZSTD(1))              COMMENT 'Event at PosFrom; empty = ENTRY',
    PosTo      Int32                  CODEC(T64, ZSTD(1))         COMMENT 'Destination path position',
    EventTo    LowCardinality(String) CODEC(ZSTD(1))              COMMENT 'Event at PosTo',
    UserCount  UInt64                 CODEC(T64, ZSTD(1))         COMMENT 'Distinct users or sessions on this edge',
    CreatedAt  DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)) DEFAULT now64(3) COMMENT 'Row insert time (UTC)',

    INDEX idx_run_time    RunTime    TYPE minmax GRANULARITY 1,
    INDEX idx_created_at  CreatedAt  TYPE minmax GRANULARITY 1,
    INDEX idx_direction   Direction  TYPE set(2) GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/journey_results_local', '{replica}')
PARTITION BY toYYYYMM(toDate(RunTime))
PRIMARY KEY (ProjectId, JourneyId, RunTime)
ORDER BY (ProjectId, JourneyId, RunTime, Direction, PosFrom, EventFrom, PosTo, EventTo)
TTL toDateTime(RunTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(RunTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.journey_results
ON CLUSTER 'pulse-clickhouse'
AS otel.journey_results_local
ENGINE = Distributed('pulse-clickhouse', otel, journey_results_local, cityHash64((ProjectId, JourneyId)));


-- =====================================================================
-- 9. otel.funnel_results_local
-- =====================================================================
CREATE TABLE otel.funnel_results_local
ON CLUSTER 'pulse-clickhouse'
(
    FunnelId           UInt64                 CODEC(T64, ZSTD(1))         COMMENT 'MySQL funnel.id',
    ProjectId          LowCardinality(String) CODEC(ZSTD(1))              COMMENT 'Project ID (proj-xxx)',
    RunTime            DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)) COMMENT 'Spark job execution time (UTC)',
    StepIndex          UInt8                  CODEC(T64, ZSTD(1))         COMMENT '0-based step index',
    StepName           LowCardinality(String) CODEC(ZSTD(1))              COMMENT 'Event name for this step',
    UserCount          UInt64                 CODEC(T64, ZSTD(1))         COMMENT 'Unique users or sessions reaching this step',
    ConversionPct      Float64                CODEC(ZSTD(1))              COMMENT 'Conversion % from step 0 to this step',
    MedianStepSeconds  Nullable(Int64)        CODEC(T64, ZSTD(1))         COMMENT 'Median seconds from previous step; NULL for step 0',
    CreatedAt          DateTime64(3, 'UTC')   CODEC(DoubleDelta, ZSTD(1)) DEFAULT now64(3) COMMENT 'Row insert time (UTC)',

    CONSTRAINT chk_StepIndex CHECK StepIndex < 32,

    INDEX idx_run_time    RunTime   TYPE minmax             GRANULARITY 1,
    INDEX idx_created_at  CreatedAt TYPE minmax             GRANULARITY 1,
    INDEX idx_step_name   StepName  TYPE bloom_filter(0.01) GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/funnel_results_local', '{replica}')
PARTITION BY toYYYYMM(toDate(RunTime))
ORDER BY (ProjectId, FunnelId, RunTime, StepIndex)
TTL toDateTime(RunTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(RunTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.funnel_results
ON CLUSTER 'pulse-clickhouse'
AS otel.funnel_results_local
ENGINE = Distributed('pulse-clickhouse', otel, funnel_results_local, cityHash64((ProjectId, FunnelId)));


-- =====================================================================
-- 10. otel.event_catalog_entries_local
--     TTL: NONE (reference/dimension table — catalog permanence)
-- =====================================================================
CREATE TABLE otel.event_catalog_entries_local
ON CLUSTER 'pulse-clickhouse'
(
    ProjectId    LowCardinality(String) CODEC(ZSTD(1)) COMMENT 'Project ID',
    FilterKey    LowCardinality(String) CODEC(ZSTD(1)) COMMENT 'Filter dimension — EVENT | APP_BUILD_NAME | OS_NAME | OS_VERSION',
    FilterValue  String                 CODEC(ZSTD(1)) COMMENT 'Distinct value for the filter dimension'
)
ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{shard}/otel/event_catalog_entries_local', '{replica}')
ORDER BY (ProjectId, FilterKey, FilterValue)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.event_catalog_entries
ON CLUSTER 'pulse-clickhouse'
AS otel.event_catalog_entries_local
ENGINE = Distributed('pulse-clickhouse', otel, event_catalog_entries_local, cityHash64(ProjectId));


-- =====================================================================
-- END
-- =====================================================================
