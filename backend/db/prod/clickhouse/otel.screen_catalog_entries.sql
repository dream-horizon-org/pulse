CREATE TABLE IF NOT EXISTS otel.screen_catalog_entries_local
ON CLUSTER 'pulse-ch'
(
    ProjectId   LowCardinality(String) COMMENT 'Project ID'  CODEC(ZSTD(1)),
    ScreenName  String                 COMMENT 'Distinct screen.name from screen_load spans' CODEC(ZSTD(1))
)
ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{shard}/otel/screen_catalog_entries_local', '{replica}')
ORDER BY (ProjectId, ScreenName)
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.screen_catalog_entries
ON CLUSTER 'pulse-ch'
AS otel.screen_catalog_entries_local
ENGINE = Distributed('pulse-ch', otel, screen_catalog_entries_local, cityHash64(ProjectId));

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.screen_catalog_entries_mv
ON CLUSTER `pulse-ch`
TO otel.screen_catalog_entries
AS
SELECT
    ProjectId,
    ScreenName
FROM otel.otel_traces_local
WHERE ProjectId != ''
  AND PulseType = 'screen_load'
  AND ScreenName != '';
