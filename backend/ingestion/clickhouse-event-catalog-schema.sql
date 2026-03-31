-- =============================================================================
-- ClickHouse: Event Catalog + Filter Values (Spark → ClickHouse)
-- =============================================================================
-- Populated by the event_catalog Spark job (backend/spark/event_catalog.py).
-- Both tables use AggregatingMergeTree with SimpleAggregateFunction so that
-- each daily Spark run can append incremental data; ClickHouse automatically
-- accumulates (sum/min/max) across runs during background merges.
--
-- Typical read queries use FINAL to trigger merge on read:
--   SELECT project_id, event_name, sumMerge(event_count) AS total_events
--   FROM otel.event_catalog FINAL
--   WHERE project_id = 'fancode'
--   GROUP BY project_id, event_name
--   ORDER BY total_events DESC;
-- =============================================================================


-- ---------------------------------------------------------------------------
-- otel.event_catalog
-- Distinct event names seen per project with aggregate counts and date range.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS otel.event_catalog
(
    project_id   LowCardinality(String)                          COMMENT 'Project ID (proj-xxx)',
    event_name   LowCardinality(String)                          COMMENT 'Custom event name',
    event_count  SimpleAggregateFunction(sum,  UInt64)           COMMENT 'Total occurrences accumulated across Spark runs',
    first_seen   SimpleAggregateFunction(min,  Date)             COMMENT 'Earliest date this event was observed',
    last_seen    SimpleAggregateFunction(max,  Date)             COMMENT 'Most recent date this event was observed',
    run_time     DateTime64(3, 'UTC')                             COMMENT 'Execution time of the Spark job'
)
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(last_seen)
ORDER BY (project_id, event_name)
SETTINGS index_granularity = 8192;


-- ---------------------------------------------------------------------------
-- otel.event_filter_values
-- Distinct values for each predefined filter dimension, scoped to
-- (project_id, event_name). Powers the filter-value dropdowns in the UI
-- when a user is building a funnel step filter.
--
-- Filter dimensions (align with Parquet column names from Vector):
--   os_name | os_version | app_build_name | device_manufacturer
--   device_model_identifier | network_carrier_icc | screen_name | service_name
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS otel.event_filter_values
(
    project_id    LowCardinality(String)                         COMMENT 'Project ID',
    event_name    LowCardinality(String)                         COMMENT 'Event this filter value was observed on',
    filter_key    LowCardinality(String)                         COMMENT 'Filter dimension name (Parquet column)',
    filter_value  String                                         COMMENT 'Observed value for this dimension',
    event_count   SimpleAggregateFunction(sum,  UInt64)          COMMENT 'Times this filter_value was seen on this event',
    run_time      DateTime64(3, 'UTC')                           COMMENT 'Execution time of the Spark job'
)
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(toDate(run_time))
ORDER BY (project_id, event_name, filter_key, filter_value)
SETTINGS index_granularity = 8192;


-- ---------------------------------------------------------------------------
-- Example queries
-- ---------------------------------------------------------------------------

-- All event names for a project, ordered by popularity
-- SELECT project_id, event_name, sumMerge(event_count) AS total
-- FROM otel.event_catalog FINAL
-- WHERE project_id = 'fancode'
-- GROUP BY project_id, event_name
-- ORDER BY total DESC
-- LIMIT 100;

-- All distinct os_version values seen on a specific event
-- SELECT filter_value, sumMerge(event_count) AS total
-- FROM otel.event_filter_values FINAL
-- WHERE project_id = 'fancode'
--   AND event_name = 'Tap:AddToCart'
--   AND filter_key = 'os_version'
-- GROUP BY filter_value
-- ORDER BY total DESC;

-- All available filter keys and their distinct value counts for a project
-- SELECT filter_key, uniq(filter_value) AS distinct_values, sumMerge(event_count) AS total
-- FROM otel.event_filter_values FINAL
-- WHERE project_id = 'fancode'
-- GROUP BY filter_key
-- ORDER BY filter_key;
