CREATE TABLE IF NOT EXISTS otel.project_monthly_usage_local
ON CLUSTER 'pulse-clickhouse'
(
    project_id    String                              CODEC(ZSTD(1)),
    month         Date                                CODEC(Delta, ZSTD(1)),
    source        LowCardinality(String)              CODEC(ZSTD(1)),
    event_count   SimpleAggregateFunction(sum, UInt64)                      CODEC(T64, ZSTD(1)),
    session_count AggregateFunction(uniqCombined64, String)                 CODEC(ZSTD(1))
    )
ENGINE = ReplicatedAggregatingMergeTree('/clickhouse/tables/{shard}/otel/project_monthly_usage', '{replica}')
PARTITION BY toYYYYMM(month)
ORDER BY (project_id, month, source)
TTL toDateTime(month) + toIntervalDay(30) TO VOLUME 'cold'
SETTINGS index_granularity = 8192, storage_policy = 'tiered';


CREATE TABLE IF NOT EXISTS otel.project_monthly_usage
ON CLUSTER 'pulse-clickhouse'
AS otel.project_monthly_usage_local
ENGINE = Distributed('pulse-clickhouse', otel, project_monthly_usage_local, cityHash64(project_id));


CREATE MATERIALIZED VIEW otel.project_monthly_logs_mv TO otel.project_monthly_usage
    ON CLUSTER 'pulse-clickhouse'
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
   FROM otel.otel_logs_local
   GROUP BY
    project_id,
    month,
    source


CREATE MATERIALIZED VIEW otel.project_monthly_traces_mv TO otel.project_monthly_usage
    ON CLUSTER 'pulse-clickhouse'
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
   FROM otel.otel_traces_local
   GROUP BY
    project_id,
    month,
    source


CREATE MATERIALIZED VIEW otel.project_monthly_stack_traces_events_mv TO otel.project_monthly_usage
    ON CLUSTER 'pulse-clickhouse'
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
   FROM otel.stack_trace_events_local
   GROUP BY
    project_id,
    month,
    source