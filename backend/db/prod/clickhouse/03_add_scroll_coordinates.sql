-- Migration: Replace scroll_x / scroll_y attributes with click.out_of_fold
--
-- SDK change: instead of sending raw scroll offsets, the SDK now:
--   1. Shifts coordinates to be content-relative (screen_x + scroll_x, screen_y + scroll_y).
--   2. Emits a boolean `click.out_of_fold = true` when the tap was outside the
--      initial visible viewport (content_x > viewportWidth || content_y > viewportHeight).
--
-- STEP 1 — otel_logs: swap ScrollXPer/ScrollYPer for OutOfFold materialized column.
--   ADD COLUMN and DROP COLUMN on otel_logs_local are both safe here — the table ORDER BY
--   is unaffected.
--
-- STEP 2 — interaction_heatmaps_daily: ScrollXBin/ScrollYBin were part of the
--   SummingMergeTree ORDER BY; they must be replaced by OutOfFold.
--   ClickHouse does NOT allow ALTER TABLE to change ORDER BY, so the table and its
--   dependent objects (MV, Distributed table) must be dropped and recreated.
--
--   Data loss: pre-migration rows had scroll bins, not OutOfFold. Dropping the table
--   loses those historical heatmap aggregates. This is acceptable because:
--     a) the coordinate system changes (content-relative vs screen-relative), making
--        old rows geometrically incompatible with new ones anyway, and
--     b) heatmaps only make sense from the moment the new SDK ships.
--
-- Run this script during a maintenance window. Apply to every shard in order.

-- ---------------------------------------------------------------------------------
-- STEP 1: otel_logs — replace scroll columns with OutOfFold
-- ---------------------------------------------------------------------------------

ALTER TABLE otel.otel_logs_local
ON CLUSTER 'pulse-ch'
DROP COLUMN IF EXISTS `ScrollXPer`,
DROP COLUMN IF EXISTS `ScrollYPer`,
ADD COLUMN IF NOT EXISTS `OutOfFold` Bool
    MATERIALIZED (LogAttributes['click.out_of_fold'] = 'true')
    CODEC(ZSTD(1))
    AFTER `AspectRatio`;

-- The Distributed table (otel.otel_logs) inherits these columns automatically.

-- ---------------------------------------------------------------------------------
-- STEP 2: interaction_heatmaps_daily — drop and recreate with OutOfFold in ORDER BY
-- ---------------------------------------------------------------------------------

-- 2a. Drop the MV first so it stops feeding the old table.
DROP TABLE IF EXISTS otel.interaction_heatmaps_daily_mv ON CLUSTER 'pulse-ch';

-- 2b. Drop the Distributed view, then the local (replicated) table.
DROP TABLE IF EXISTS otel.interaction_heatmaps_daily ON CLUSTER 'pulse-ch';
DROP TABLE IF EXISTS otel.interaction_heatmaps_daily_local ON CLUSTER 'pulse-ch';

-- 2c. Recreate the local table with OutOfFold in ORDER BY.
CREATE TABLE IF NOT EXISTS otel.interaction_heatmaps_daily_local
ON CLUSTER 'pulse-ch'
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
  `OutOfFold`          Bool                         CODEC(ZSTD(1)),
  `WeightNormal`       UInt64                       CODEC(T64, ZSTD(1)),
  `WeightRage`         UInt64                       CODEC(T64, ZSTD(1)),
  `WeightDead`         UInt64                       CODEC(T64, ZSTD(1)),

  INDEX idx_xy (XBin, YBin) TYPE minmax GRANULARITY 4
)
ENGINE = ReplicatedSummingMergeTree(
    '/clickhouse/tables/{shard}/otel/interaction_heatmaps_daily_local',
    '{replica}',
    (WeightNormal, WeightRage, WeightDead)
)
PARTITION BY toYYYYMM(Date)
PRIMARY KEY (Date, ProjectId, ScreenName)
ORDER BY (Date, ProjectId, ScreenName, AppVersion, Platform, GeographicalRegion, Breakpoint, XBin, YBin, OutOfFold)
TTL Date + INTERVAL 7 DAY TO VOLUME 'cold',
    Date + INTERVAL 90 DAY DELETE
SETTINGS
    storage_policy = 'tiered',
    index_granularity = 8192;

-- 2d. Recreate the Distributed table.
CREATE TABLE IF NOT EXISTS otel.interaction_heatmaps_daily
ON CLUSTER 'pulse-ch'
AS otel.interaction_heatmaps_daily_local
ENGINE = Distributed('pulse-ch', otel, interaction_heatmaps_daily_local, cityHash64(ProjectId));

-- 2e. Recreate the materialized view.
CREATE MATERIALIZED VIEW IF NOT EXISTS otel.interaction_heatmaps_daily_mv
ON CLUSTER 'pulse-ch'
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
    OutOfFold,
    count() AS WeightNormal,
    countIf(Rage) AS WeightRage,
    countIf(ClickType = 'dead') AS WeightDead
FROM otel.otel_logs
WHERE PulseType = 'app.click'
GROUP BY Date, ProjectId, ScreenName, AppVersion, Platform, GeoState, Breakpoint, XBin, YBin, OutOfFold;
