# session-replay-ingestion — plan

Component brief: [`docs/components/session-replay-ingestion.md`](../../components/session-replay-ingestion.md).

## Scope

Consume `session_recording_events`, batch by partition/session in
memory, Snappy-compress each session block, write the merged batch to
S3 in one PUT, then publish per-block metadata to a Kafka topic that a
ClickHouse Kafka engine table consumes. Delivery is at-least-once;
ClickHouse handles dedupe.

## Architecture sketch

```
Kafka: session_recording_events
        |
        v
[SessionReplayConsumer]
        |    rebalance: discard in-mem state for revoked partitions
        v
[SessionBatchManager] -> [SnappySessionRecorder] (per block)
        |
        |  flush when size >= MAX_BATCH_SIZE_KB OR age >= MAX_BATCH_AGE_MS
        v
[S3SessionBatchFileStorage] (single PUT, streaming)
        |
        v
[KafkaProducer] -> clickhouse_session_replay_events  (block metadata)
        |
        v
ClickHouse (AggregatingMergeTree) dedupes
```

Then `KafkaOffsetManager` commits Kafka consumer offsets.

## Sub-components

Core:

- [core/kafka-consumer.md](./core/kafka-consumer.md)
- [core/batching.md](./core/batching.md)
- [core/s3-writer.md](./core/s3-writer.md)

## Cross-links

- Producer: [`session-capture-service`](../session-capture-service/index.md).
- Sibling: [`heatmap-screenshot-ingestion`](../heatmap-screenshot-ingestion/index.md).

## Risks

- In-memory batches up to 100 MiB per consumer — memory pressure under
  bursty traffic.
- Rebalance during a large in-memory batch → discarded, must replay.
- S3 PUT timeouts (30 s default) on large batches; crash path is
  intentional but watch restart-loop on persistent S3 errors.
- No tests configured yet; add Vitest/Jest harness.
