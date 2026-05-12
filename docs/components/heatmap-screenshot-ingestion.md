# heatmap-screenshot-ingestion

## What

Sibling Kafka consumer to `session-replay-ingestion`. Consumes the same
`session_recording_events` topic under a dedicated consumer group, but
filters to messages that contain both a META event and a full-snapshot
screenshot in the same `snapshot_items` batch. For each match it
uploads a heatmap-screenshot JSON to S3, using Redis for per-project
dedupe and quota enforcement so we don't store duplicate snapshots.

## Path + stack

- Path: `backend/heatmap-screenshot-ingestion/`
- Language: TypeScript 5 on Node.js 20 (`package.json`,
  `tsconfig.json`).
- Kafka: `node-rdkafka` ^2.18.0.
- S3: `@aws-sdk/client-s3`.
- Redis (dedupe + quota): `ioredis` ^5.10.1.
- Validation: `zod` ^3.
- Time: `luxon`.
- Tests: `vitest` ^2.1.0 (`vitest.config.ts`,
  `**/*.test.ts` colocated in `src/`).
- Tracing: `@opentelemetry/auto-instrumentations-node`.
- Package: `pulse-heatmap-screenshot-ingestion@0.1.1-SNAPSHOT`.

## Build

```bash
cd backend/heatmap-screenshot-ingestion
yarn install
yarn build                      # tsc → dist/
yarn start                      # node dist/index.js
yarn test                       # vitest run
yarn test:watch
```

Container: `Dockerfile`.

## Inputs + outputs

Inputs:

- Kafka topic `session_recording_events` (separate group from
  `session-replay-ingestion`).
- Redis: optional, gated by `heatmapQuotaEnabled` and
  `heatmapDedupeEnabled` config flags; keys built by
  `buildHeatmapDedupeKey` and `buildHeatmapQuotaKey`.
- Env: brokers, S3 bucket/prefix/region, `REDIS_URL`,
  breakpoint-rules config.

Outputs:

- S3 JSON object per accepted screenshot. Key shape from
  `buildHeatmapS3ObjectKey` (project / date / app-version / path /
  breakpoint / uuid). Body via `heatmapJsonBody(...)`. Tags via
  `buildIngestionS3ObjectTagging`.
- Redis writes for dedupe-marker + quota-counter.
- No Kafka producer — terminal sink for the heatmap pipeline.

## Key files

- `src/index.ts` — entrypoint + public re-exports
  (`HeatmapScreenshotConsumer`, `extractHeatmapScreenshot`,
  `buildHeatmapS3ObjectKey`, etc.).
- `src/consumer.ts` — `HeatmapScreenshotConsumer` (Kafka loop, dedupe +
  quota gating, S3 upload).
- `src/config.ts` — env → `Config` (+ `resolveRedisUrlFromEnv`).
- `src/heatmap-extract.ts` — finds META + full-snapshot pair within
  `snapshot_items`.
- `src/heatmap-redis.ts` — `HeatmapRedis` wrapper, key builders.
- `src/breakpoint-rules.ts` — `resolveHeatmapBreakpoint` (responsive
  bucketing).
- `src/s3-key.ts` — key + path-segment sanitization helpers,
  `appVersionForPath`, `heatmapJsonBody`,
  `utcDateTagYyyyMmDdFromMillis`, `buildIngestionS3ObjectTagging`.
- `src/s3-upload.ts` — `createS3Client`, `putJsonWithRetry`.
- `src/kafka/{message-parser,offset-manager,types}.ts` — message
  parsing + offset accounting.
- Tests: `heatmap-extract.test.ts`, `s3-key.test.ts`,
  `breakpoint-rules.test.ts`, `heatmap-redis.test.ts`,
  `config.test.ts`.

## Owners

- Owner: _TBD_
- Backup: _TBD_

## Plan

See [`docs/plans/heatmap-screenshot-ingestion/index.md`](../plans/heatmap-screenshot-ingestion/index.md).

## Peers

- Upstream producer: [`session-capture-service`](./session-capture-service.md)
- Sibling consumer: [`session-replay-ingestion`](./session-replay-ingestion.md)
- Downstream rollups: [`spark-jobs`](./spark-jobs.md)
