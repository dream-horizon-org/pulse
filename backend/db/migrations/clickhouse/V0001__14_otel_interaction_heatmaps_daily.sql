--liquibase formatted sql

--changeset db-migrations:V0001__14_otel_interaction_heatmaps_daily runOnChange:false failOnError:true splitStatements:true endDelimiter:; dbms:clickhouse
--comment Baseline: otel.interaction_heatmaps_daily SummingMergeTree table and interaction_heatmaps_daily_mv materialized view.

CREATE TABLE IF NOT EXISTS otel.interaction_heatmaps_daily
(
  `Date`               Date                         CODEC(DoubleDelta, ZSTD(1)),
  `ProjectId`          LowCardinality(String)       CODEC(ZSTD(1)),
  `ScreenName`         LowCardinality(String)       CODEC(ZSTD(1)),
  `AppVersion`         LowCardinality(String)       CODEC(ZSTD(1)),
  `Platform`           LowCardinality(String)       CODEC(ZSTD(1)),
  `GeographicalRegion` LowCardinality(String)       CODEC(ZSTD(1)),
  `Breakpoint`         LowCardinality(String)       CODEC(ZSTD(1)),
  `XBin`               Float32                      CODEC(Gorilla, ZSTD(1)),
  `YBin`               Float32                      CODEC(Gorilla, ZSTD(1)),
  `WeightNormal`       UInt64                       CODEC(T64, ZSTD(1)),
  `WeightRage`         UInt64                       CODEC(T64, ZSTD(1)),
  `WeightDead`         UInt64                       CODEC(T64, ZSTD(1))
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(Date)
PRIMARY KEY (Date, ProjectId, ScreenName)
ORDER BY (Date, ProjectId, ScreenName, AppVersion, Platform, GeographicalRegion, Breakpoint, XBin, YBin)
SETTINGS index_granularity = 8192;


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.interaction_heatmaps_daily_mv
TO otel.interaction_heatmaps_daily AS
SELECT
    toDate(Timestamp) AS Date,
    ProjectId,
    ScreenName,
    AppVersion,
    Platform,
    GeoState AS GeographicalRegion,
    multiIf(
        Platform = 'Web' AND ViewportWidth > 1024, 'Web_Extra_Large',
        Platform = 'Web', 'Mobile_Medium',
        ViewportWidth > 600, 'Tablet_Large',
        ViewportWidth <= 600 AND (ViewportHeight / ViewportWidth) <= 1.5, 'Mobile_Small',
        'Mobile_Medium'
    ) AS Breakpoint,
    round(NormXPer, 2) AS XBin,
    round(NormYPer, 2) AS YBin,
    count() AS WeightNormal,
    countIf(Rage) AS WeightRage,
    countIf(ClickType = 'dead') AS WeightDead
FROM otel.otel_logs
WHERE PulseType = 'app.click'
GROUP BY Date, ProjectId, ScreenName, AppVersion, Platform, GeoState, Breakpoint, XBin, YBin;

--rollback empty
