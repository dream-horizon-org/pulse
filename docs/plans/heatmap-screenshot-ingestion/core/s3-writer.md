# Heatmap-Screenshot · S3 Writer

Uploads screenshot binaries to S3 keyed by project + session + viewport breakpoint.

Brief: [../../../components/heatmap-screenshot-ingestion.md](../../../components/heatmap-screenshot-ingestion.md) · Peers: [kafka-consumer](./kafka-consumer.md), [redis-cache](./redis-cache.md).

## Purpose

After a screenshot passes redis-dedup + breakpoint classification, persist it to S3 so the UI heatmap overlay can fetch it. Keys are stable so re-uploads overwrite (idempotent on retries).

## Source location

- `backend/heatmap-screenshot-ingestion/src/s3-upload.ts` — upload logic.
- `backend/heatmap-screenshot-ingestion/src/s3-key.ts` — key builder.
- `backend/heatmap-screenshot-ingestion/src/s3-key.test.ts` — key tests.
- `backend/heatmap-screenshot-ingestion/src/breakpoint-rules.ts` — viewport bucket selection.
- `backend/heatmap-screenshot-ingestion/src/config.ts` — S3 bucket + AWS config.

Entry point: `backend/heatmap-screenshot-ingestion/src/index.ts`.

## Public surface

Internal only. Exports from `s3-upload.ts`:
- `uploadScreenshot(key: string, buffer: Buffer, contentType: string): Promise<void>`
From `s3-key.ts`:
- `buildScreenshotKey({ projectId, screenName, breakpoint, hash }): string`

## Internal design

1. Consumer (`consumer.ts`) reads Kafka batches.
2. For each message: extract viewport → `breakpoint-rules.ts` maps to a canonical bucket (e.g. `mobile`, `tablet`, `desktop`).
3. `heatmap-redis.ts` checks for duplicate via `(projectId, screenName, breakpoint, contentHash)`; skips if seen.
4. On miss, `s3-upload.ts` writes PNG to `s3://<bucket>/screenshots/<projectId>/<screen>/<breakpoint>/<hash>.png` via `@aws-sdk/client-s3` `PutObjectCommand`.
5. Caches the hash in Redis with a configurable TTL.

## Dependencies

- `@aws-sdk/client-s3` ^3.
- `ioredis` for dedup (see [redis-cache.md](./redis-cache.md)).
- Kafka consumer (`node-rdkafka`) upstream.

## Data contracts

- S3 key: `screenshots/{projectId}/{screenName}/{breakpoint}/{hash}.png`
- `breakpoint` ∈ `{mobile, tablet, desktop}` (see `breakpoint-rules.ts`).
- Object metadata: `x-amz-meta-project-id`, `x-amz-meta-session-id`.

## Tests

`src/s3-key.test.ts`, `src/breakpoint-rules.test.ts`, `src/heatmap-redis.test.ts` (vitest).

## History / decisions

Keyed by content hash so retransmits dedup at the bucket level too (defense in depth vs. Redis TTL expiry).

## Rebuild recipe

1. Create `s3-key.ts` with a pure key-builder + tests.
2. Wrap the AWS SDK v3 client; reuse across invocations.
3. Pipe: Kafka msg → breakpoint rule → Redis check → S3 put → Redis mark.
4. Surface failures as OTel non-fatal spans via the auto-instrumentation.
