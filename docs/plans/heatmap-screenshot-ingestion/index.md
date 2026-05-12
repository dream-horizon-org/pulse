# heatmap-screenshot-ingestion — plan

Component brief: [`docs/components/heatmap-screenshot-ingestion.md`](../../components/heatmap-screenshot-ingestion.md).

## Scope

Sibling consumer to session-replay-ingestion. From the same Kafka
topic, pick out the messages that contain both a META event and a
full-snapshot screenshot in the same `snapshot_items` batch and write
one heatmap-screenshot JSON to S3, with Redis-backed dedupe and quota
to avoid duplicate or runaway uploads.

## Architecture sketch

```
Kafka: session_recording_events (group: heatmap-*)
        |
        v
[HeatmapScreenshotConsumer]
        |
        v
[extractHeatmapScreenshot]  (META + full snapshot present?)
        |   no -> drop
        v
[resolveHeatmapBreakpoint]  (responsive bucket)
        |
        v
[HeatmapRedis] check dedupe + quota (optional, gated)
        |   blocked -> drop
        v
[buildHeatmapS3ObjectKey] + [heatmapJsonBody]
        |
        v
S3 (putJsonWithRetry) + Redis dedupe-marker / quota-counter
```

## Sub-components

Core:

- [core/kafka-consumer.md](./core/kafka-consumer.md)
- [core/redis-cache.md](./core/redis-cache.md)
- [core/s3-writer.md](./core/s3-writer.md)

## Cross-links

- Upstream: [`session-capture-service`](../session-capture-service/index.md).
- Sibling: [`session-replay-ingestion`](../session-replay-ingestion/index.md).
- Downstream rollups: [`spark-jobs`](../spark-jobs/index.md) (when
  added).

## Risks

- Redis outage with `heatmapDedupeEnabled=true` blocks all uploads —
  document fail-open vs fail-closed policy.
- S3 PUT retry loop without ceiling can drift offsets; ensure
  `putJsonWithRetry` has bounded attempts.
- META vs snapshot ordering across partitions: handled by requiring
  both within the same `snapshot_items` batch in `heatmap-extract`.
