# s3-writer

Parent: [session-replay-ingestion](../index.md) ·
Brief: [component](../../../components/session-replay-ingestion.md)

## 1. Purpose

Upload a flushed batch (many Snappy-compressed blocks) to S3 as a
single object, then produce per-block metadata to Kafka so ClickHouse
can locate each block by offset within the S3 file.

## 2. Source

- `src/sessions/s3-session-batch-writer.ts` — `S3SessionBatchFileStorage`.
- `src/sessions/session-batch-file-storage.ts` — file-storage interface.
- `src/kafka/producer.ts` — emits to `kafkaMetadataTopic`
  (`clickhouse_session_replay_events`).

## 3. Config

- `s3Endpoint` (default `http://localhost:9000` — MinIO in dev),
- `s3Region` (default `us-east-1`),
- `s3Bucket` (default `pulse-session-replay`),
- `s3Prefix` (default `session-recordings`),
- `s3AccessKeyId`, `s3SecretAccessKey` (optional → IAM role / EC2
  metadata),
- `s3TimeoutMs` (default 30 000).

## 4. Upload

Uses `@aws-sdk/client-s3` + `@aws-sdk/lib-storage` (multipart-capable
upload). Today the design is "single PUT of the merged batch file" —
`lib-storage` will fall back to multipart automatically above 5 MiB.

## 5. Object key

Prefix + partition + UTC date + uuid. Exact key layout is computed in
`s3-session-batch-writer.ts`. Keep the prefix stable; the ClickHouse
side parses it.

## 6. Metadata produce

After PUT succeeds, the writer hands the block-index to `KafkaProducer`
(see `src/kafka/producer.ts`) which produces one record per block to
`clickhouse_session_replay_events`. Only after that succeeds do we
commit consumer offsets.

## 7. Failure modes

- S3 5xx / timeout → throw → process exits (k8s restart, Kafka
  replays).
- Kafka produce fail post-PUT → next run produces a duplicate S3
  object. Acceptable because ClickHouse AggregatingMergeTree dedupes
  by block id.

## 8. Tests

Mock the S3 client; assert single-PUT behavior and key format. Add a
contract test for metadata record shape.

## 9. Cross-links

- [kafka-consumer](./kafka-consumer.md)
- [batching](./batching.md)
- Downstream: ClickHouse Kafka engine table consuming
  `clickhouse_session_replay_events`.
