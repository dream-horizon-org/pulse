-- =============================================================================
-- ClickHouse: Pre-computed journey results (Spark → ClickHouse)
-- =============================================================================
-- Journey definitions are stored in MySQL (journey table).
-- Spark reads the definition + S3 Parquet and writes aggregated path-graph rows here.
--
-- Each row is a directed edge in the user path graph:
--   "user_count users transitioned from event_from@pos_from to event_to@pos_to"
--
-- ENTRY nodes: pos_from = -1, event_from = ''
--   Represents "N users triggered the anchor event" (anchor always at pos_to = 0).
--
-- START direction: positions 0 (anchor), 1, 2, ... (events after anchor)
-- END direction:   positions 0 (anchor), -1, -2, ... (events before anchor)
--
-- Exposed on GET /v1/journeys/{id} as journeyResults (nodes + links).
-- =============================================================================

CREATE TABLE IF NOT EXISTS otel.journey_results
(
    journey_id   String        COMMENT 'Same as MySQL journey.id (stored as string)',
    project_id   String        COMMENT 'Project ID',
    run_time     DateTime64(3, 'UTC') COMMENT 'Execution time of the Spark job',
    direction    String        COMMENT 'START | END',
    pos_from     Int32         COMMENT 'Path position of source node; -1 = ENTRY',
    event_from   String        COMMENT 'Event name at pos_from; empty = ENTRY',
    pos_to       Int32         COMMENT 'Path position of destination node',
    event_to     String        COMMENT 'Event name at pos_to',
    user_count   UInt64        COMMENT 'Unique users (or sessions) who made this transition',
    created_at   DateTime64(3) DEFAULT now64(3)
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(toDate(run_time))
ORDER BY (journey_id, run_time, direction, pos_from, event_from, pos_to, event_to)
SETTINGS index_granularity = 8192;
