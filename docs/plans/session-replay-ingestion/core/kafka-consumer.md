# kafka-consumer

Parent: [session-replay-ingestion](../index.md) ·
Brief: [component](../../../components/session-replay-ingestion.md)

## 1. Purpose

Pull batches of recording events from Kafka with partition-aware
offset tracking and clean rebalance semantics.

## 2. Source

- `src/consumer.ts` — `SessionReplayConsumer` (lifecycle, consume loop).
- `src/kafka/message-parser.ts` — Zod schema for raw records.
- `src/kafka/offset-manager.ts` — per-partition offsets.
- `src/kafka/types.ts` — `ParsedMessageData`, `RawKafkaMessage`.
- `src/kafka/producer.ts` — metadata producer (downstream).

## 3. Config

- `kafkaBrokers`, `kafkaTopic` (`session_recording_events`),
  `kafkaGroupId` (`session-replay-ingestion-v1`),
  `kafkaMetadataTopic` (`clickhouse_session_replay_events`),
  `fetchBatchSize` (500).

## 4. Loop

1. Connect consumer + producer (`node-rdkafka`).
2. Check S3 storage health.
3. Subscribe.
4. Pull batch → parse + validate (Zod) → record into
   `SessionBatchManager` keyed by partition → session.
5. If size/age threshold met → flush (see [batching](./batching.md)).
6. After successful flush + metadata produce → commit offsets via
   `KafkaOffsetManager`.

## 5. Rebalance

On partition revocation, discard in-memory data for those partitions.
The new owner will replay from the last committed offset
(at-least-once). ClickHouse AggregatingMergeTree dedupes.

## 6. Failure

If flush fails (S3 / Kafka produce error), process crashes by design.
Kubernetes restarts → replay from last committed offset.

## 7. Tests

None configured today. Add Vitest with a fake `node-rdkafka` (or
`kafkajs` test harness) covering: clean consume, rebalance discard,
partial flush failure.

## 8. Cross-links

- [batching](./batching.md)
- [s3-writer](./s3-writer.md)
- Upstream: [session-capture-service](../../session-capture-service/index.md)

## 9. Open items

- Add structured logger (today: `console.log`).
- Surface lag metric per partition for HPA.
