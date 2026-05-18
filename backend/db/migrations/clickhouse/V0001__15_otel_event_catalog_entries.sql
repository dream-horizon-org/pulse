--liquibase formatted sql

--changeset db-migrations:V0001__15_otel_event_catalog_entries runOnChange:false failOnError:true splitStatements:true endDelimiter:; dbms:clickhouse
--comment Baseline: otel.event_catalog_entries ReplacingMergeTree table and 4 materialized views for EVENT, APP_BUILD_NAME, OS_VERSION, OS_NAME filter dimensions.

CREATE TABLE IF NOT EXISTS otel.event_catalog_entries
(
    ProjectId    LowCardinality(String) COMMENT 'Project ID'                                                  CODEC(ZSTD(1)),
    FilterKey    LowCardinality(String) COMMENT 'Filter dimension — EVENT | APP_BUILD_NAME | OS_NAME | OS_VERSION' CODEC(ZSTD(1)),
    FilterValue  String                 COMMENT 'Distinct value for the filter dimension'                     CODEC(ZSTD(1))
)
ENGINE = ReplacingMergeTree
ORDER BY (ProjectId, FilterKey, FilterValue)
SETTINGS index_granularity = 8192;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.event_catalog_entries_mv_event
TO otel.event_catalog_entries
AS
SELECT
    ProjectId,
    CAST('EVENT' AS LowCardinality(String)) AS FilterKey,
    Body AS FilterValue
FROM otel.otel_logs
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND Body != '';

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.event_catalog_entries_mv_app_build_name
TO otel.event_catalog_entries
AS
SELECT
    ProjectId,
    CAST('APP_BUILD_NAME' AS LowCardinality(String)) AS FilterKey,
    ResourceAttributes['app.build_name'] AS FilterValue
FROM otel.otel_logs
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND ifNull(ResourceAttributes['app.build_name'], '') != '';

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.event_catalog_entries_mv_os_version
TO otel.event_catalog_entries
AS
SELECT
    ProjectId,
    CAST('OS_VERSION' AS LowCardinality(String)) AS FilterKey,
    ResourceAttributes['os.version'] AS FilterValue
FROM otel.otel_logs
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND ifNull(ResourceAttributes['os.version'], '') != '';

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.event_catalog_entries_mv_os_name
TO otel.event_catalog_entries
AS
SELECT
    ProjectId,
    CAST('OS_NAME' AS LowCardinality(String)) AS FilterKey,
    ResourceAttributes['os.name'] AS FilterValue
FROM otel.otel_logs
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND ifNull(ResourceAttributes['os.name'], '') != '';

-- ---------------------------------------------------------------------------
-- Legacy tables (deprecated — drop after backfill/migration if they exist)
-- ---------------------------------------------------------------------------
-- otel.event_catalog
-- otel.event_filter_values

-- ---------------------------------------------------------------------------
-- Example queries (use FINAL for deduplicated keys)
-- ---------------------------------------------------------------------------

-- Event names
-- SELECT FilterValue FROM otel.event_catalog_entries FINAL
-- WHERE ProjectId = 'fancode' AND FilterKey = 'EVENT' ORDER BY FilterValue;
-- App build names
-- SELECT FilterValue FROM otel.event_catalog_entries FINAL
-- WHERE ProjectId = 'fancode' AND FilterKey = 'APP_BUILD_NAME' ORDER BY FilterValue;

--rollback empty
