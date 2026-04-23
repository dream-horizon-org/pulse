CREATE TABLE IF NOT EXISTS otel.otel_metrics_summary_local
  ON CLUSTER 'pulse-ch'
(
  `ResourceAttributes`        Map(LowCardinality(String), String) CODEC(ZSTD(3)),
  `ResourceSchemaUrl`         String                              CODEC(ZSTD(1)),
  `ScopeName`                 String                              CODEC(ZSTD(1)),
  `ScopeVersion`              String                              CODEC(ZSTD(1)),
  `ScopeAttributes`           Map(LowCardinality(String), String) CODEC(ZSTD(3)),
  `ScopeDroppedAttrCount`     UInt32                              CODEC(T64, ZSTD(1)),
  `ScopeSchemaUrl`            String                              CODEC(ZSTD(1)),
  `ServiceName`               LowCardinality(String)              CODEC(ZSTD(1)),
  `MetricName`                String                              CODEC(ZSTD(1)),
  `MetricDescription`         String                              CODEC(ZSTD(3)),
  `MetricUnit`                String                              CODEC(ZSTD(1)),
  `Attributes`                Map(LowCardinality(String), String) CODEC(ZSTD(3)),
  `StartTimeUnix`             DateTime64(9)                       CODEC(DoubleDelta, ZSTD(1)),
  `TimeUnix`                  DateTime64(9)                       CODEC(DoubleDelta, ZSTD(1)),
  `Count`                     UInt64                              CODEC(T64, ZSTD(1)),
  `Sum`                       Float64                             CODEC(Gorilla, ZSTD(1)),
  `ValueAtQuantiles.Quantile` Array(Float64)                      CODEC(ZSTD(3)),
  `ValueAtQuantiles.Value`    Array(Float64)                      CODEC(Gorilla, ZSTD(1)),
  `Flags`                     UInt32                              CODEC(T64, ZSTD(1)),

  `ProjectId`         LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], '')                         CODEC(ZSTD(1)),
  `SessionId`         String                 MATERIALIZED ifNull(Attributes['session.id'], '')                                 CODEC(ZSTD(1)),
  `MeteringSessionId` String                 MATERIALIZED ifNull(Attributes['pulse.metering.session.id'], '')                  CODEC(ZSTD(1)),
  `AppVersion`        LowCardinality(String) MATERIALIZED ifNull(Attributes['app.build_name'], '')                             CODEC(ZSTD(1)),
  `SDKVersion`        LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['rum.sdk.version'], '')                    CODEC(ZSTD(1)),
  `Platform`          LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], '')                            CODEC(ZSTD(1)),
  `OsVersion`         LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.version'], '')                         CODEC(ZSTD(1)),
  `GeoState`          LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.region.iso_code'], '')                        CODEC(ZSTD(1)),
  `GeoCountry`        LowCardinality(String) MATERIALIZED ifNull(Attributes['geo.country.iso_code'], '')                       CODEC(ZSTD(1)),
  `DeviceModel`       LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], '')                  CODEC(ZSTD(1)),
  `NetworkProvider`   LowCardinality(String) MATERIALIZED ifNull(Attributes['network.carrier.name'], '')                       CODEC(ZSTD(1)),
  `UserId`            String                 MATERIALIZED ifNull(nullIf(Attributes['user.id'], ''), ifNull(Attributes['app.installation.id'], '')) CODEC(ZSTD(1)),

  INDEX idx_app_version AppVersion TYPE set(100)           GRANULARITY 4,
  INDEX idx_platform    Platform   TYPE set(10)            GRANULARITY 4,
  INDEX idx_session     SessionId  TYPE bloom_filter(0.01) GRANULARITY 4,
  INDEX idx_user        UserId     TYPE bloom_filter(0.01) GRANULARITY 4
  )
  ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/otel/otel_metrics_summary_local', '{replica}')
  PARTITION BY toDate(TimeUnix)
  PRIMARY KEY (ProjectId, ServiceName, MetricName)
  ORDER BY (ProjectId, ServiceName, MetricName, Attributes, toUnixTimestamp64Nano(TimeUnix))
  TTL toDateTime(TimeUnix) + INTERVAL 7 DAY  TO VOLUME 'cold',
  toDateTime(TimeUnix) + INTERVAL 90 DAY DELETE
SETTINGS
    storage_policy    = 'tiered',
    index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.otel_metrics_summary
  ON CLUSTER 'pulse-ch'
AS otel.otel_metrics_summary_local
  ENGINE = Distributed('pulse-ch', otel, otel_metrics_summary_local, cityHash64((ProjectId, MetricName)));


-- Unified read surface for performance queries (METRICS dataType); underlying tables receive collector INSERTs
CREATE VIEW IF NOT EXISTS otel.otel_metrics
  (
  `Timestamp` DateTime64(9),
  `ServiceName` LowCardinality(String),
  `MetricName` String,
  `Value` Float64,
  `Count` Nullable(UInt64),
  `Sum` Nullable(Float64),
  `Attributes` Map(LowCardinality(String), String),
  `ResourceAttributes` Map(LowCardinality(String), String),
  `ProjectId` LowCardinality(String),
  `Flags` UInt32,
  `MetricSource` LowCardinality(String)
  ) AS
SELECT
  TimeUnix AS Timestamp,
    ServiceName,
    MetricName,
    Value,
    CAST(NULL AS Nullable(UInt64)) AS Count,
    CAST(NULL AS Nullable(Float64)) AS Sum,
    Attributes,
    ResourceAttributes,
    ProjectId,
    Flags,
    CAST('gauge' AS LowCardinality(String)) AS MetricSource
FROM otel.otel_metrics_gauge
UNION ALL
SELECT
  TimeUnix,
  ServiceName,
  MetricName,
  Value,
  CAST(NULL AS Nullable(UInt64)),
  CAST(NULL AS Nullable(Float64)),
  Attributes,
  ResourceAttributes,
  ProjectId,
  Flags,
  CAST('sum' AS LowCardinality(String))
FROM otel.otel_metrics_sum
UNION ALL
SELECT
  TimeUnix,
  ServiceName,
  MetricName,
  Sum AS Value,
    CAST(Count AS Nullable(UInt64)),
    CAST(Sum AS Nullable(Float64)),
    Attributes,
    ResourceAttributes,
    ProjectId,
    Flags,
    CAST('summary' AS LowCardinality(String))
FROM otel.otel_metrics_summary
UNION ALL
SELECT
  TimeUnix,
  ServiceName,
  MetricName,
  Sum AS Value,
    CAST(Count AS Nullable(UInt64)),
    CAST(Sum AS Nullable(Float64)),
    Attributes,
    ResourceAttributes,
    ProjectId,
    Flags,
    CAST('histogram' AS LowCardinality(String))
FROM otel.otel_metrics_histogram
UNION ALL
SELECT
  TimeUnix,
  ServiceName,
  MetricName,
  Sum AS Value,
    CAST(Count AS Nullable(UInt64)),
    CAST(Sum AS Nullable(Float64)),
    Attributes,
    ResourceAttributes,
    ProjectId,
    Flags,
    CAST('exp_histogram' AS LowCardinality(String))
FROM otel.otel_metrics_exp_histogram;


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_metrics_summary_mv
TO otel.project_monthly_usage
AS SELECT
            ProjectId AS project_id,
            toStartOfMonth(TimeUnix) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
   FROM otel.otel_metrics_summary
   GROUP BY project_id, month, source;