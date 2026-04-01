-- =============================================================================
-- Pulse Heatmap / Interaction — ClickHouse Schema
-- =============================================================================
-- Per-click events for heatmaps (app.widget.click).
-- Layout: PascalCase, ProjectId-first ORDER BY, daily partitions (see otel_traces).
--
-- SDK / OTEL attribute mapping (ingest maps OTLP keys → columns):
--
-- Click event (app.widget.click):
--   ClickType          ← click.type                    ("good" | "dead")
--   Rage               ← click.is_rage                 (only on rage events)
--   RageCount          ← click.rageCount               (when click.is_rage)
--   Timestamp          ← span/log wall-clock (setTimestamp at tap)
--   ScreenCoordinateX  ← app.screen.coordinate.x       (raw px)
--   ScreenCoordinateY  ← app.screen.coordinate.y       (raw px)
--   XPer               ← app.screen.coordinate.nx      (0.0–1.0)
--   YPer               ← app.screen.coordinate.ny      (0.0–1.0)
--   ViewportWidth      ← device.screen.width           (dp/pt at tap)
--   ViewportHeight     ← device.screen.height          (dp/pt at tap)
--
-- Resource (global on signal):
--   AppVersion         ← service.version               (e.g. versionName_code)
--   Platform           ← os.name                       (e.g. Android, iOS)
--   AspectRatio        ← device.screen.aspect_ratio    (e.g. 9:20; often at init)
--   ProjectId          ← project.id                    (e.g. 1234567890)
--
-- Span/log processor:
--   ScreenName         ← screen.name
--
-- Optional enrichment / product:
--   GeographicalRegion — from collector or geo.* when available
--   Id, TraceId, SpanId, ComponentId, InteractionScore — product / correlation
-- =============================================================================

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
    `ComponentId` LowCardinality(String) , -- TODO: Add component id
    `InteractionScore` Float32 , -- TODO: Add interaction score
    `InteractionId` LowCardinality(String), -- TODO: Add interaction index
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
    `WeightDead` UInt64,                  -- Frustration: Dead taps
    `ComponentId` LowCardinality(String),
    `InteractionId` LowCardinality(String),
    `TotalInteractionScore` Float32,
    `TotalInteractionCount` UInt32
) 
ENGINE = SummingMergeTree()
ORDER BY (Date,ProjectId, ScreenName, AppVersion, Platform, GeographicalRegion, Breakpoint, XBin, YBin, ComponentId, InteractionId);


CREATE MATERIALIZED VIEW  IF NOT EXISTS otel.interaction_heatmaps_daily_mv 
TO otel.interaction_heatmaps_daily AS
SELECT
    toDate(Timestamp) AS Date,
    ProjectId,
    ScreenName,
    AppVersion,
    Platform,
    GeoState AS GeographicalRegion,
    ComponentId,
    InteractionId,
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
    countIf(Rage = True) AS WeightRage,
    countIf(ClickType = 'dead') AS WeightDead,

    sumIf(InteractionScore, InteractionId != '') AS TotalInteractionScore,
    toUInt32(countIf(InteractionId != '')) AS TotalInteractionCount
FROM otel.interaction_events
GROUP BY Date, ProjectId, ScreenName, AppVersion, Platform, GeoState, Breakpoint, XBin, YBin, ComponentId, InteractionId;


