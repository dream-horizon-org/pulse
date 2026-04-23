CREATE TABLE IF NOT EXISTS otel.otel_logs
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
    `UserId`              String                  MATERIALIZED ifNull(LogAttributes['user.id'], '')                                                                    CODEC(ZSTD(1)),
    `AppIntstallationId`  String                  MATERIALIZED ifNull(LogAttributes['app.installation.id'], '')                                                        CODEC(ZSTD(1)),
    `PulseType`           LowCardinality(String)  MATERIALIZED ifNull(LogAttributes['pulse.type'], 'otel')                                                             CODEC(ZSTD(1)),
    `EventName`           LowCardinality(String)  MATERIALIZED if(ifNull(LogAttributes['pulse.type'], 'otel') = 'custom_event', Body, '')                              CODEC(ZSTD(1)),
    `ScreenName`          LowCardinality(String)  MATERIALIZED ifNull(LogAttributes['screen.name'], ''),                                                               CODEC(ZSTD(1)),

    INDEX idx_trace_id      TraceId        TYPE bloom_filter(0.001) GRANULARITY 1,
    INDEX idx_session_id    SessionId      TYPE bloom_filter(0.001) GRANULARITY 1,
    INDEX idx_user_id       UserId         TYPE bloom_filter(0.001) GRANULARITY 1,
    INDEX idx_span_id       SpanId         TYPE bloom_filter(0.001) GRANULARITY 4,
    INDEX idx_severity_num  SeverityNumber TYPE set(32)              GRANULARITY 1
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(Timestamp)
ORDER BY (ProjectId, PulseType, EventName, Timestamp)
SETTINGS index_granularity  = 8192;