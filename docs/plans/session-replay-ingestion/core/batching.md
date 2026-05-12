# batching

Parent: [session-replay-ingestion](../index.md) ·
Brief: [component](../../../components/session-replay-ingestion.md)

## 1. Purpose

Group incoming Kafka messages into per-session "blocks" and pack many
blocks into one S3 batch file, so we minimize PUT count without
blowing memory.

## 2. Source

- `src/sessions/session-batch-manager.ts` — top-level batch state.
- `src/sessions/session-batch-recorder.ts` — append events to a block,
  rotate on size/age.
- `src/sessions/snappy-session-recorder.ts` — Snappy compression per
  block.
- `src/sessions/session-block-metadata.ts` — per-block metadata row.
- `src/sessions/session-metadata-store.ts` — per-session window state.

## 3. Keys

Records are bucketed by **partition → session id**. Partition is the
unit of Kafka ordering; per-session is the unit of replay.

## 4. Thresholds

From `src/config.ts`:

- `MAX_BATCH_SIZE_KB` (default 102 400 = 100 MiB) →
  `maxBatchSizeBytes`.
- `MAX_BATCH_AGE_MS` (default 10 000).

When either threshold trips for the whole batch, flush.

## 5. Compression

Each session block is Snappy-compressed
(`snappy` npm) inside `snappy-session-recorder.ts` before being
appended to the merged batch file. Snappy is chosen for speed; final
S3 object is a concatenation of compressed blocks plus an index in the
metadata stream.

## 6. Memory budget

Worst case: `maxBatchSizeBytes` of compressed payload + working buffers
+ session metadata. Container memory must comfortably exceed
`maxBatchSizeBytes × 2`.

## 7. Tests

Add Vitest cases: threshold rotation, partition revocation drops
in-flight block, Snappy round-trip.

## 8. Cross-links

- [kafka-consumer](./kafka-consumer.md)
- [s3-writer](./s3-writer.md)

## 9. Open items

- Add `maxBlocksPerBatch` cap to bound block-index size.
- Telemetry: histogram of flush sizes and ages, blocks-per-batch.
