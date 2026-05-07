CREATE TABLE otel.session_summary_local
ON CLUSTER 'pulse-ch'
(
    ProjectId           LowCardinality(String) CODEC(ZSTD(1)),
    sessionId           String                 CODEC(ZSTD(1)),
    startTime           SimpleAggregateFunction(min, DateTime64(9, 'UTC')) CODEC(DoubleDelta, ZSTD(1)),
    endTime             SimpleAggregateFunction(max, DateTime64(9, 'UTC')) CODEC(DoubleDelta, ZSTD(1)),
    userId              SimpleAggregateFunction(any, String)                CODEC(ZSTD(1)),
    platform            SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    appVersion          SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    osVersion           SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    deviceModel         SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    networkProvider     SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    geoCountry          SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    geoRegion           SimpleAggregateFunction(any, LowCardinality(String)) CODEC(ZSTD(1)),
    apdexSum            SimpleAggregateFunction(sum, Float64) CODEC(ZSTD(1)),
    apdexCount          SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    networkErrors       SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    interactionErrors   SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    crashCount          SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    anrCount            SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    nonFatal            SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),
    slowInteractionCount SimpleAggregateFunction(sum, UInt64) CODEC(T64, ZSTD(1)),
    frozenFrameCount    SimpleAggregateFunction(sum, Float64) CODEC(ZSTD(1)),
    spanCount           SimpleAggregateFunction(sum, UInt64)  CODEC(T64, ZSTD(1)),

    INDEX idx_user_id     userId      TYPE bloom_filter(0.01) GRANULARITY 1,
    INDEX idx_start_time  startTime   TYPE minmax             GRANULARITY 1,
    INDEX idx_end_time    endTime     TYPE minmax             GRANULARITY 1,
    INDEX idx_crash_count crashCount  TYPE minmax             GRANULARITY 1,
    INDEX idx_anr_count   anrCount    TYPE minmax             GRANULARITY 1,
    INDEX idx_nonfatal    nonFatal    TYPE minmax             GRANULARITY 1
    )
ENGINE = ReplicatedAggregatingMergeTree('/clickhouse/tables/{shard}/otel/session_summary', '{replica}')
PARTITION BY (toYYYYMMDD(startTime))
ORDER BY (ProjectId, sessionId)
TTL toDateTime(startTime) + toIntervalDay(7)  TO VOLUME 'cold',
    toDateTime(startTime) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';


CREATE TABLE IF NOT EXISTS otel.session_summary
ON CLUSTER 'pulse-ch'
AS otel.session_summary_local
    ENGINE = Distributed('pulse-ch', otel, session_summary_local, cityHash64(sessionId));


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.session_crash_mv
       ON CLUSTER 'pulse-ch'
       TO otel.session_summary
(
    `ProjectId` LowCardinality(String),
    `sessionId` String,
    `startTime` DateTime64(9, 'UTC'),
    `endTime` DateTime64(9, 'UTC'),
    `crashCount` UInt64,
    `anrCount` UInt64,
    `nonFatal` UInt64
)
AS SELECT
            ProjectId,
            SessionId AS sessionId,
            min(Timestamp) AS startTime,
            max(Timestamp) AS endTime,
            countIf(PulseType = 'device.crash') AS crashCount,
            countIf(PulseType = 'device.anr') AS anrCount,
            countIf(PulseType = 'non_fatal') AS nonFatal
   FROM otel.stack_trace_events_local
   WHERE SessionId != ''
   GROUP BY
            ProjectId,
            SessionId;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.session_summary_mv
       ON CLUSTER 'pulse-ch'
       TO otel.session_summary
(
    `ProjectId` LowCardinality(String),
    `sessionId` String,
    `startTime` DateTime64(9, 'UTC'),
    `endTime` DateTime64(9, 'UTC'),
    `userId` String,
    `platform` String,
    `appVersion` String,
    `osVersion` String,
    `deviceModel` String,
    `networkProvider` String,
    `geoCountry` String,
    `geoRegion` String,
    `apdexSum` Float64,
    `apdexCount` UInt64,
    `networkErrors` UInt64,
    `interactionErrors` UInt64,
    `slowInteractionCount` UInt64,
    `frozenFrameCount` Float64,
    `spanCount` UInt64
)
AS SELECT
            ProjectId,
            SessionId AS sessionId,
            min(Timestamp) AS startTime,
            max(Timestamp) AS endTime,
            any(UserId) AS userId,
            any(Platform) AS platform,
            any(AppVersion) AS appVersion,
            any(OsVersion) AS osVersion,
            any(DeviceModel) AS deviceModel,
            any(NetworkProvider) AS networkProvider,
            any(GeoCountry) AS geoCountry,
            any(GeoState) AS geoRegion,
            sumIf(toFloat64OrZero(SpanAttributes['pulse.interaction.apdex_score']), (SpanAttributes['pulse.interaction.apdex_score']) != '') AS apdexSum,
            countIf((SpanAttributes['pulse.interaction.apdex_score']) != '') AS apdexCount,
            countIf(StatusCode = 'Error') AS networkErrors,
            countIf(ifNull(SpanAttributes['pulse.interaction.is_error'], '') = 'true') AS interactionErrors,
            countIf(ifNull(SpanAttributes['pulse.interaction.user_category'], '') = 'Poor') AS slowInteractionCount,
            sum(toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count'])) AS frozenFrameCount,
            count() AS spanCount
   FROM otel.otel_traces_local
   WHERE SessionId != ''
   GROUP BY
            ProjectId,
            SessionId;


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.session_summary_replay_mv
       ON CLUSTER 'pulse-ch'
       TO otel.session_summary
(
    `ProjectId` LowCardinality(String),
    `sessionId` String,
    `startTime` SimpleAggregateFunction(min, DateTime64(6, 'UTC')),
    `endTime` SimpleAggregateFunction(max, DateTime64(6, 'UTC')),
    `userId` String
)
AS SELECT
            ProjectId,
            SessionId AS sessionId,
            min(MinFirstTimestamp) AS startTime,
            max(MaxLastTimestamp) AS endTime,
            any(UserId) AS userId
   FROM otel.session_replay_events_local
   WHERE (SessionId != '') AND (ProjectId != '') AND (MinFirstTimestamp IS NOT NULL) AND (MaxLastTimestamp IS NOT NULL)
   GROUP BY
            ProjectId,
            SessionId;