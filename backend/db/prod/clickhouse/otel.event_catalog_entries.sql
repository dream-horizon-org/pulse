CREATE TABLE IF NOT EXISTS otel.event_catalog_entries_local
ON CLUSTER 'pulse-ch'
(
    ProjectId    LowCardinality(String) COMMENT 'Project ID'                                                  CODEC(ZSTD(1)),
    FilterKey    LowCardinality(String) COMMENT 'Filter dimension — EVENT | APP_BUILD_NAME | OS_NAME | OS_VERSION' CODEC(ZSTD(1)),
    FilterValue  String                 COMMENT 'Distinct value for the filter dimension'                     CODEC(ZSTD(1))
)
ENGINE = ReplicatedReplacingMergeTree('/clickhouse/tables/{shard}/otel/event_catalog_entries_local', '{replica}')
ORDER BY (ProjectId, FilterKey, FilterValue)
SETTINGS index_granularity = 8192;


CREATE TABLE IF NOT EXISTS otel.event_catalog_entries
ON CLUSTER 'pulse-ch'
AS otel.event_catalog_entries_local
ENGINE = Distributed('pulse-ch', otel, event_catalog_entries_local, cityHash64(ProjectId));

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.event_catalog_entries_mv_event
ON CLUSTER `pulse-ch`
TO otel.event_catalog_entries
AS
SELECT
    ProjectId,
    CAST('EVENT' AS LowCardinality(String)) AS FilterKey,
    Body AS FilterValue
FROM otel.otel_logs_local
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND Body != '';

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.event_catalog_entries_mv_app_build_name
ON CLUSTER `pulse-ch`
TO otel.event_catalog_entries
AS
SELECT
    ProjectId,
    CAST('APP_BUILD_NAME' AS LowCardinality(String)) AS FilterKey,
    ResourceAttributes['app.build_name'] AS FilterValue
FROM otel.otel_logs_local
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND ifNull(ResourceAttributes['app.build_name'], '') != '';

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.event_catalog_entries_mv_os_version
ON CLUSTER `pulse-ch`
TO otel.event_catalog_entries
AS
SELECT
    ProjectId,
    CAST('OS_VERSION' AS LowCardinality(String)) AS FilterKey,
    ResourceAttributes['os.version'] AS FilterValue
FROM otel.otel_logs_local
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND ifNull(ResourceAttributes['os.version'], '') != '';

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.event_catalog_entries_mv_os_name
ON CLUSTER `pulse-ch`
TO otel.event_catalog_entries
AS
SELECT
    ProjectId,
    CAST('OS_NAME' AS LowCardinality(String)) AS FilterKey,
    ResourceAttributes['os.name'] AS FilterValue
FROM otel.otel_logs_local
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND ifNull(ResourceAttributes['os.name'], '') != '';