CREATE TABLE IF NOT EXISTS otel.screen_catalog_entries
(
    ProjectId   LowCardinality(String) COMMENT 'Project ID'  CODEC(ZSTD(1)),
    ScreenName  String                 COMMENT 'Distinct screen.name from screen_load spans' CODEC(ZSTD(1))
)
ENGINE = ReplacingMergeTree
ORDER BY (ProjectId, ScreenName)
SETTINGS index_granularity = 8192;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.screen_catalog_entries_mv
TO otel.screen_catalog_entries
AS
SELECT
    ProjectId,
    ScreenName
FROM otel.otel_traces
WHERE ProjectId != ''
  AND PulseType = 'screen_load'
  AND ScreenName != '';

-- ---------------------------------------------------------------------------
-- Example query (use FINAL for deduplicated keys)
-- ---------------------------------------------------------------------------
-- SELECT ScreenName FROM otel.screen_catalog_entries FINAL
-- WHERE ProjectId = 'fancode' ORDER BY ScreenName;
