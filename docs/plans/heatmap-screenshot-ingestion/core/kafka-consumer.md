# kafka-consumer

Parent: [heatmap-screenshot-ingestion](../index.md) ·
Brief: [component](../../../components/heatmap-screenshot-ingestion.md)

## 1. Purpose

Consume `session_recording_events` with a dedicated consumer group,
filter to messages whose `snapshot_items` carries both META and a
full-snapshot screenshot, and feed downstream extract / dedupe / S3
stages.

## 2. Source

- `src/consumer.ts` — `HeatmapScreenshotConsumer`.
- `src/kafka/message-parser.ts` — `KafkaMessageParser`,
  `RawKafkaMessage`.
- `src/kafka/offset-manager.ts` — `KafkaOffsetManager`.
- `src/kafka/types.ts` — message types.
- `src/heatmap-extract.ts` — `extractHeatmapScreenshot`.

## 3. Lifecycle

1. `validateRedisEnv()`.
2. `createHeatmapRedis(config)` and `ping()` (or skip if disabled).
3. Connect `node-rdkafka` consumer with own group id.
4. Loop: pull → parse → extract → (optional) dedupe + quota → upload.
5. Commit offsets through `KafkaOffsetManager` after upload success.
6. SIGTERM/SIGINT → set `stopping = true`, disconnect.

## 4. Filtering

`extractHeatmapScreenshot(snapshot_items)` returns a screenshot only
when both META and a full snapshot are present. Otherwise the message
is dropped (still committed; we don't replay forever).

## 5. Logging

`console.log` with `[HeatmapConsumer]` prefix. Promote to structured
logger when consolidating with replay service.

## 6. Failure modes

- Kafka error code branch: `ErrorCodes.ERR__PARTITION_EOF` is normal
  (idle).
- Parse failure → log + skip + commit (poison-pill containment).
- Redis down with dedupe enabled → must decide fail-open vs fail-closed
  (see [redis-cache](./redis-cache.md)).

## 7. Tests

`vitest run` — colocated tests:

- `heatmap-extract.test.ts`
- `s3-key.test.ts`
- `breakpoint-rules.test.ts`
- `heatmap-redis.test.ts`
- `config.test.ts`

Consumer integration test TBD.

## 8. Cross-links

- [redis-cache](./redis-cache.md)
- [s3-writer](./s3-writer.md)
- Sibling: [session-replay-ingestion / kafka-consumer](../../session-replay-ingestion/core/kafka-consumer.md)

## 9. Open items

- Distinct consumer group id constant — confirm in `config.ts`.
- Backoff on Redis ping failure during start.
