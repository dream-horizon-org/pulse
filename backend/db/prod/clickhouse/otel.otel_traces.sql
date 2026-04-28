CREATE TABLE IF NOT EXISTS otel.otel_traces_local
ON CLUSTER 'pulse-ch'
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
    Platform           LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], '')                 CODEC(ZSTD(1)),
    OsVersion          LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], '')              CODEC(ZSTD(1)),
    GeoState           LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['geo.region.iso_code'], '')         CODEC(ZSTD(1)),
    GeoCountry         LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['geo.country.iso_code'], '')        CODEC(ZSTD(1)),
    DeviceModel        LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.identifier'], '') CODEC(ZSTD(1)),
    NetworkProvider    LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['network.carrier.name'], '')        CODEC(ZSTD(1)),
    MeteringSessionId  String                 MATERIALIZED ifNull(SpanAttributes['metering.session.id'], '')         CODEC(ZSTD(1)),
    UserId             String                 MATERIALIZED ifNull(SpanAttributes['user.id'], '')                     CODEC(ZSTD(1)),
    HttpUrl            String                 MATERIALIZED ifNull(SpanAttributes['http.url'], ifNull(SpanAttributes['url.full'], '')) CODEC(ZSTD(3)),
    HttpHost           LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['net.peer.name'], ifNull(SpanAttributes['server.address'], '')) CODEC(ZSTD(1)),
    HttpMethod         LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['http.method'], ifNull(SpanAttributes['http.request.method'], '')) CODEC(ZSTD(1)),
    HttpStatusCode     UInt16                 MATERIALIZED toUInt16OrZero(ifNull(SpanAttributes['http.status_code'], ifNull(SpanAttributes['http.response.status_code'], '0'))) CODEC(T64, ZSTD(1)),

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
    INDEX idx_ts            Timestamp         TYPE minmax              GRANULARITY 1,
    INDEX idx_http_host    HttpHost           TYPE bloom_filter(0.01)  GRANULARITY 1,
    INDEX idx_http_method  HttpMethod         TYPE set(16)             GRANULARITY 1,
    INDEX idx_http_status  HttpStatusCode     TYPE minmax              GRANULARITY 1
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/otel_traces_local', '{replica}')
PARTITION BY toYYYYMMDD(Timestamp)
ORDER BY (ProjectId, PulseType, SpanName, Timestamp)
TTL toDateTime(Timestamp) + INTERVAL 7  DAY TO VOLUME 'cold',
    toDateTime(Timestamp) + INTERVAL 90 DAY DELETE
SETTINGS index_granularity = 8192, storage_policy = 'tiered';

CREATE TABLE IF NOT EXISTS otel.otel_traces
ON CLUSTER 'pulse-ch'
AS otel.otel_traces_local
ENGINE = Distributed('pulse-ch', otel, otel_traces_local, cityHash64(TraceId));


-- Optional follow-up (NOT applied): network-span hot columns.
-- Apply separately if you proceed with the network-query optimization:
--   ALTER TABLE otel.otel_traces_local ON CLUSTER 'pulse-ch'
--     ADD COLUMN HttpUrl        String                 MATERIALIZED ifNull(SpanAttributes['http.url'], ifNull(SpanAttributes['url.full'], '')) CODEC(ZSTD(3)),
--     ADD COLUMN HttpHost       LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['net.peer.name'], ifNull(SpanAttributes['server.address'], '')) CODEC(ZSTD(1)),
--     ADD COLUMN HttpMethod     LowCardinality(String) MATERIALIZED ifNull(SpanAttributes['http.method'], ifNull(SpanAttributes['http.request.method'], '')) CODEC(ZSTD(1)),
--     ADD COLUMN HttpStatusCode UInt16                 MATERIALIZED toUInt16OrZero(ifNull(SpanAttributes['http.status_code'], ifNull(SpanAttributes['http.response.status_code'], '0'))) CODEC(T64, ZSTD(1)),
--     ADD INDEX  idx_http_host    HttpHost       TYPE bloom_filter(0.01) GRANULARITY 1,
--     ADD INDEX  idx_http_method  HttpMethod     TYPE set(16)            GRANULARITY 1,
--     ADD INDEX  idx_http_status  HttpStatusCode TYPE minmax             GRANULARITY 1;