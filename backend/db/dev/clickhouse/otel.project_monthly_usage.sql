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
