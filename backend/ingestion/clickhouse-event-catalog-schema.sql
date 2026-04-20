-- =============================================================================
-- ClickHouse: Unified event catalog (Spark + live otel_logs)
-- =============================================================================
-- Distinct filter catalog per project. FilterKey identifies the dimension:
--   EVENT            → FilterValue = custom event name (Body / event_name in Spark path)
--   APP_BUILD_NAME   → FilterValue = app build label (ResourceAttributes['app.build_name'])
--   OS_VERSION       → FilterValue = OS version (ResourceAttributes['os.version'])
--   OS_NAME          → FilterValue = OS name (ResourceAttributes['os.name'])
--
-- Column names: **PascalCase** (consistent with `otel.otel_traces`, funnel/journey results).
--
-- **Engine:** Single-node `ReplacingMergeTree` — local Docker / dev without ZooKeeper.
-- For **multi-replica** production, create `event_catalog_entries_local` +
-- `event_catalog_entries` from `clickhouse-event-catalog-replicated-schema.sql`.
--
-- **Writers (same table; dedup on merge, use FINAL for reads):**
--   * **Spark:** `backend/spark` EventCatalogJob (S3 / EVENTS_INCREMENTAL).
--   * **Materialized view:** `event_catalog_entries_mv` → inserts into this table from
--     `otel.otel_logs` on each log insert (`PulseType = 'custom_event'` only; aligns with
--     `ClickhouseAnalyticsConstantsMapper`: `Body` for EVENT, `ResourceAttributes['os.name']`,
--     `['os.version']`, `['app.build_name']` for the dimension keys — no reliance on optional
--     materialized aliases (`Platform`, `OsVersion`, `AppVersion`).
--
-- **Apply MV after** `otel.otel_logs` exists (see `clickhouse-otel-schema.sql`).
-- Historical logs before MV creation require a one-time `INSERT INTO ... SELECT ... FROM otel.otel_logs`.
--
-- Incremental S3 windows: Spark reads MAX(started_at) from MySQL analytics_jobs for the latest
-- SUCCEEDED EVENTS_INCREMENTAL run; if none, scans 7 days of S3.
--
-- If you previously created event_catalog_entries with snake_case columns, migrate:
--   CREATE TABLE otel.event_catalog_entries_new (...); INSERT ...; EXCHANGE TABLES; ...
-- =============================================================================

CREATE TABLE IF NOT EXISTS otel.event_catalog_entries
(
    `ProjectId`   LowCardinality(String) COMMENT 'Project ID',
    `FilterKey`   LowCardinality(String) COMMENT 'EVENT | APP_BUILD_NAME | OS_VERSION | OS_NAME',
    `FilterValue` String                  COMMENT 'Distinct value for that filter key'
)
ENGINE = ReplacingMergeTree
ORDER BY (ProjectId, FilterKey, FilterValue)
SETTINGS index_granularity = 8192;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.event_catalog_entries_mv
TO otel.event_catalog_entries
AS
SELECT
    ProjectId,
    CAST('EVENT' AS LowCardinality(String)) AS FilterKey,
    Body AS FilterValue
FROM otel.otel_logs
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND Body != ''
UNION ALL
SELECT
    ProjectId,
    CAST('APP_BUILD_NAME' AS LowCardinality(String)) AS FilterKey,
    ResourceAttributes['app.build_name'] AS FilterValue
FROM otel.otel_logs
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND ifNull(ResourceAttributes['app.build_name'], '') != ''
UNION ALL
SELECT
    ProjectId,
    CAST('OS_VERSION' AS LowCardinality(String)) AS FilterKey,
    ResourceAttributes['os.version'] AS FilterValue
FROM otel.otel_logs
WHERE ProjectId != ''
  AND PulseType = 'custom_event'
  AND ifNull(ResourceAttributes['os.version'], '') != ''
UNION ALL
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
