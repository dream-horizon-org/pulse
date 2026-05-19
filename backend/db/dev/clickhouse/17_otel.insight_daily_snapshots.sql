CREATE TABLE IF NOT EXISTS otel.insight_daily_snapshots
(
    ProjectId    LowCardinality(String),
    InsightType  LowCardinality(String),
    EntityKey    LowCardinality(String),
    SnapshotDate Date,
    ComputedData String COMMENT 'JSON: computed aggregated metrics for this day and insight type',
    ComputedAt   DateTime64(3, 'UTC')
)
ENGINE = ReplacingMergeTree(ComputedAt)
PARTITION BY toYYYYMM(SnapshotDate)
ORDER BY (ProjectId, InsightType, EntityKey, SnapshotDate)
TTL toDateTime(SnapshotDate) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192;
