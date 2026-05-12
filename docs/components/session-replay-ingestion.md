# session-replay-ingestion

## What

Kafka consumer that batches recording events from
`session_recording_events`, compresses each session-block with Snappy,
uploads the merged batch file to S3 (single PUT), and publishes
per-block metadata to a downstream Kafka topic
(`clickhouse_session_replay_events`) for ClickHouse indexing.

Delivery is at-least-once: on crash, the process restarts and Kafka
replays from the last committed offset; duplicate blocks are harmless
because ClickHouse's AggregatingMergeTree dedupes them (see header
comment in `consumer.ts`).

## Path + stack

- Path: `backend/session-replay-ingestion/`
- Language: TypeScript 5 on Node.js 20 (`package.json`,
  `tsconfig.json`).
- Kafka: `node-rdkafka` ^2.18.0 (consumer + producer).
- S3: `@aws-sdk/client-s3` + `@aws-sdk/lib-storage` (multipart-capable).
- Compression: `snappy` ^7.
- Validation: `zod` ^3.
- Time / IDs: `luxon`, `uuid` v9.
- Tracing: `@opentelemetry/auto-instrumentations-node`.
- Test framework: none configured today (no `test` script).

## Build

```bash
cd backend/session-replay-ingestion
yarn install                    # or npm install
yarn build                      # tsc → dist/
yarn start                      # node dist/index.js
yarn dev                        # ts-node src/index.ts
```

Container: `Dockerfile`.

## Inputs + outputs

Inputs:

- Kafka consumer group `session-replay-ingestion-v1` (default,
  `KAFKA_GROUP_ID`), topic `session_recording_events`
  (`KAFKA_TOPIC`), default brokers `localhost:9092`.
- Env: `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`
  (default `pulse-session-replay`), `S3_PREFIX`
  (default `session-recordings`), optional
  `S3_ACCESS_KEY_ID` / `S3_SECRET_ACCESS_KEY`.
- Batching knobs: `MAX_BATCH_SIZE_KB` (default 102400 = 100 MiB),
  `MAX_BATCH_AGE_MS` (default 10 000), `S3_TIMEOUT_MS` (30 000),
  `FETCH_BATCH_SIZE` (500).

Outputs:

- S3 object per flushed batch: one file containing many
  Snappy-compressed session blocks, keyed by S3 prefix +
  partition/session layout (see `s3-session-batch-writer.ts`).
- Kafka produce to `KAFKA_METADATA_TOPIC`
  (default `clickhouse_session_replay_events`): one record per block
  with offsets into the S3 file → consumed by ClickHouse Kafka engine
  table.

## Key files

- `src/index.ts` — entrypoint, SIGTERM/SIGINT graceful shutdown.
- `src/consumer.ts` — `SessionReplayConsumer` (consume loop, rebalance
  handling, flush trigger).
- `src/config.ts` — env → typed `Config` interface.
- `src/types.ts` — shared message + block types.
- `src/kafka/message-parser.ts` — Zod-validated payload parser.
- `src/kafka/offset-manager.ts` — tracks per-partition high-water.
- `src/kafka/producer.ts` — wraps `node-rdkafka` `Producer`.
- `src/kafka/types.ts` — `ParsedMessageData`, `RawKafkaMessage`.
- `src/sessions/session-batch-manager.ts` — in-memory grouping
  partition → session → block.
- `src/sessions/session-batch-recorder.ts` — appends events to a block,
  rotates on size/age.
- `src/sessions/snappy-session-recorder.ts` — Snappy compression
  per block.
- `src/sessions/session-block-metadata.ts` — emits metadata rows.
- `src/sessions/session-metadata-store.ts` — keeps per-session window
  state.
- `src/sessions/session-batch-file-storage.ts` — file-storage interface.
- `src/sessions/s3-session-batch-writer.ts` — single-PUT upload of the
  merged batch file.

## Owners

- Owner: _TBD_
- Backup: _TBD_

## Plan

See [`docs/plans/session-replay-ingestion/index.md`](../plans/session-replay-ingestion/index.md).

## Peers

- Producer: [`session-capture-service`](./session-capture-service.md)
- Sibling consumer: [`heatmap-screenshot-ingestion`](./heatmap-screenshot-ingestion.md)
