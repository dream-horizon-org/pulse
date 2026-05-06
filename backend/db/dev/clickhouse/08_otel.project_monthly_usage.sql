CREATE TABLE IF NOT EXISTS otel.project_monthly_usage
(
    project_id    String                              CODEC(ZSTD(1)),
    month         Date                                CODEC(Delta, ZSTD(1)),
    source        LowCardinality(String)              CODEC(ZSTD(1)),
    event_count   SimpleAggregateFunction(sum, UInt64)                      CODEC(T64, ZSTD(1)),
    session_count AggregateFunction(uniqCombined64, String)                 CODEC(ZSTD(1))
    )
ENGINE = AggregatingMergeTree
PARTITION BY toYYYYMM(month)
ORDER BY (project_id, month, source)
SETTINGS index_granularity = 8192;


CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_logs_mv TO otel.project_monthly_usage
(
    `project_id` LowCardinality(String),
    `month` Date,
    `source` String,
    `event_count` UInt64,
    `session_count` AggregateFunction(uniqCombined64, String)
)
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(Timestamp) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
   FROM otel.otel_logs
   GROUP BY
    project_id,
    month,
    source;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_traces_mv TO otel.project_monthly_usage
(
    `project_id` LowCardinality(String),
    `month` Date,
    `source` String,
    `event_count` UInt64,
    `session_count` AggregateFunction(uniqCombined64, String)
)
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(Timestamp) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
   FROM otel.otel_traces
   GROUP BY
              project_id,
              month,
              source;

CREATE MATERIALIZED VIEW IF NOT EXISTS otel.project_monthly_stack_traces_events_mv TO otel.project_monthly_usage
(
    `project_id` LowCardinality(String),
    `month` Date,
    `source` String,
    `event_count` UInt64,
    `session_count` AggregateFunction(uniqCombined64, String)
)
AS SELECT
    ProjectId AS project_id,
    toStartOfMonth(Timestamp) AS month,
    'otel' AS source,
    count() AS event_count,
    uniqCombined64StateIf(MeteringSessionId, MeteringSessionId != '') AS session_count
   FROM otel.stack_trace_events
   GROUP BY
              project_id,
              month,
              source;
