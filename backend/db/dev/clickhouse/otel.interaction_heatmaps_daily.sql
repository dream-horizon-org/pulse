CREATE TABLE IF NOT EXISTS otel.interaction_heatmaps_daily
(
    `Date` Date,
    `ProjectId` LowCardinality(String),
    `ScreenName` LowCardinality(String),
    `AppVersion` LowCardinality(String),
    `Platform` LowCardinality(String),
    `GeographicalRegion` LowCardinality(String),
    `Breakpoint` LowCardinality(String),
    `XBin` Float32,
    `YBin` Float32,
    `WeightNormal` UInt64,
    `WeightRage` UInt64,
    `WeightDead` UInt64
    )
    ENGINE = SummingMergeTree()
    ORDER BY (Date, ProjectId, ScreenName, AppVersion, Platform, GeographicalRegion, Breakpoint, XBin, YBin);


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