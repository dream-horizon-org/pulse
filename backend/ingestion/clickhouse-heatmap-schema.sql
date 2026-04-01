CREATE TABLE IF NOT EXISTS otel.interaction_events
(
    `Id` String CODEC(ZSTD(1)),
    `TraceId` String CODEC(ZSTD(1)),
    `Timestamp` DateTime64(9, 'UTC') CODEC(Delta(8), ZSTD(1)),
    `ServiceName` LowCardinality(String) CODEC(ZSTD(1)),
    `ResourceAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `LogAttributes` Map(LowCardinality(String), String) CODEC(ZSTD(1)),
    `ProjectId` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['project.id'], ''),
    `ClickType` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['click.type'], ''),
    `Rage` Bool MATERIALIZED (LogAttributes['click.is_rage'] = 'true'),
    `RageCount` UInt8 MATERIALIZED toUInt8OrZero(LogAttributes['click.rageCount']),
    `ScreenName` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['screen.name'], ''),
    `AppVersion` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['app.build_name'], ''),
    `AspectRatio` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['device.screen.aspect_ratio'], ''),
    `ViewportWidth` UInt16 MATERIALIZED toUInt16OrZero(LogAttributes['device.screen.width']),
    `ViewportHeight` UInt16 MATERIALIZED toUInt16OrZero(LogAttributes['device.screen.height']),
    `Platform` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['os.name'], ''),
    `GeoState` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['geo.region.iso_code'], ''),
    `GeoCountry` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['geo.country.iso_code'], ''),
    `DeviceModel` LowCardinality(String) MATERIALIZED ifNull(ResourceAttributes['device.model.name'], ''),
    `NetworkProvider` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['network.carrier.name'], ''),
    `XPer` Float32 MATERIALIZED toFloat32OrZero(LogAttributes['app.screen.coordinate.x']),
    `YPer` Float32 MATERIALIZED toFloat32OrZero(LogAttributes['app.screen.coordinate.y']),
    `NormXPer` Float32 MATERIALIZED toFloat32OrZero(LogAttributes['app.screen.coordinate.nx']),
    `NormYPer` Float32 MATERIALIZED toFloat32OrZero(LogAttributes['app.screen.coordinate.ny']),
    `UserId` String MATERIALIZED ifNull(nullIf(LogAttributes['user.id'], ''), ifNull(LogAttributes['app.installation.id'], '')),
    `PulseType` LowCardinality(String) MATERIALIZED ifNull(LogAttributes['pulse.type'], 'otel'),
    `MeteringSessionId` String MATERIALIZED ifNull(LogAttributes['pulse.metering.session.id'], ''),
    INDEX idx_trace_id TraceId TYPE bloom_filter(0.001) GRANULARITY 1
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(Timestamp)
ORDER BY (ProjectId, ScreenName, Timestamp, TraceId)
SETTINGS index_granularity = 8192;


CREATE TABLE IF NOT EXISTS otel.interaction_heatmaps_daily (
    `Date` Date,
    `ProjectId` LowCardinality(String),
    `ScreenName` LowCardinality(String),
    `AppVersion` LowCardinality(String),
    `Platform` LowCardinality(String),   
    `GeographicalRegion` LowCardinality(String),
    `Breakpoint` LowCardinality(String),   -- mapped from aspect ratio and viewport width
    `XBin` Float32,
    `YBin` Float32,
    `WeightNormal` UInt64,                -- Total clicks volume
    `WeightRage` UInt64,                  -- Frustration: Rage taps
    `WeightDead` UInt64                   -- Frustration: Dead taps
) 
ENGINE = SummingMergeTree()
ORDER BY (Date,ProjectId, ScreenName, AppVersion, Platform, GeographicalRegion, Breakpoint, XBin, YBin);


CREATE MATERIALIZED VIEW  IF NOT EXISTS otel.interaction_heatmaps_daily_mv 
TO otel.interaction_heatmaps_daily AS
SELECT
    toDate(Timestamp) AS Date,
    ProjectId,
    ScreenName,
    AppVersion,
    Platform,
    GeoState AS GeographicalRegion,

    -- Mapping Logic gate
    multiIf(
        -- Web (Extra Large - px)
        Platform = 'Web' AND ViewportWidth > 1024, 'Web_Extra_Large',

        -- Web (Medium fallback)
        Platform = 'Web', 'Mobile_Medium',

        -- Tablet (Large - dp)
        ViewportWidth > 600, 'Tablet_Large',

        -- Mobile (Small - dp)
        ViewportWidth <= 600 AND (ViewportHeight / ViewportWidth) <= 1.5, 'Mobile_Small',

        -- Mobile (Medium)
        'Mobile_Medium'
    ) AS Breakpoint,
    
    -- Coordinate Binning
    round(NormXPer, 2) AS XBin,
    round(NormYPer, 2) AS YBin,
    
    -- Weight Summation
    count() AS WeightNormal,
    countIf(Rage) AS WeightRage,
    countIf(ClickType = 'dead') AS WeightDead

FROM otel.interaction_events
GROUP BY Date, ProjectId, ScreenName, AppVersion, Platform, GeoState, Breakpoint, XBin, YBin;


