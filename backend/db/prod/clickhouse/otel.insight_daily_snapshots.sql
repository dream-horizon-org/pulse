CREATE TABLE otel.insight_daily_snapshots
ON CLUSTER 'pulse-ch'
(
    ProjectId    LowCardinality(String)  CODEC(ZSTD(1)),
    InsightType  LowCardinality(String)  CODEC(ZSTD(1)),
    EntityKey    LowCardinality(String)  CODEC(ZSTD(1)),
    SnapshotDate Date                    CODEC(Delta, ZSTD(1)),
    ComputedData String                  COMMENT 'JSON: computed aggregated metrics for this day and insight type' CODEC(ZSTD(3)),
    ComputedAt   DateTime64(3, 'UTC')    CODEC(DoubleDelta, ZSTD(1))
)
ENGINE = ReplicatedReplacingMergeTree(
    '/clickhouse/tables/{shard}/otel/insight_daily_snapshots', '{replica}',
    ComputedAt)
PARTITION BY toYYYYMM(SnapshotDate)
ORDER BY (ProjectId, InsightType, EntityKey, SnapshotDate)
TTL toDateTime(SnapshotDate) + toIntervalDay(90) DELETE
SETTINGS index_granularity = 8192;

CREATE TABLE IF NOT EXISTS otel.insight_daily_snapshots
ON CLUSTER 'pulse-ch'
AS otel.insight_daily_snapshots_local
ENGINE = Distributed('pulse-ch', otel, insight_daily_snapshots_local, cityHash64(ProjectId, InsightType, EntityKey));
