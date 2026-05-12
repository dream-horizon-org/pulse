# redis-cache

Parent: [heatmap-screenshot-ingestion](../index.md) ·
Brief: [component](../../../components/heatmap-screenshot-ingestion.md)

## 1. Purpose

Dedupe duplicate heatmap screenshots and enforce per-project upload
quotas. Both features are independently gated by config so we can run
with neither in dev.

## 2. Source

- `src/heatmap-redis.ts` — `HeatmapRedis`,
  `buildHeatmapDedupeKey(...)`, `buildHeatmapQuotaKey(...)`,
  `createHeatmapRedis(config)`.
- `src/config.ts` — `heatmapDedupeEnabled`, `heatmapQuotaEnabled`,
  `resolveRedisUrlFromEnv()`.

## 3. Backend

`ioredis@^5.10` client. Connection url resolved via
`resolveRedisUrlFromEnv` (env: `REDIS_URL` or component-parts).

## 4. Keys

- Dedupe: `buildHeatmapDedupeKey(projectId, path, appVersion,
  breakpoint, screenshotHash)` (exact shape in source). TTL ensures the
  marker eventually expires.
- Quota: `buildHeatmapQuotaKey(projectId, periodBucket)` —
  monotonically incremented; compared to per-project ceiling.

## 5. Decision flow

```
if dedupe enabled AND key exists -> drop
if quota  enabled AND count >= limit -> drop
else
  upload to S3
  if upload ok:
    SET dedupe key (with TTL)
    INCR quota key
```

## 6. Gating

If both `heatmapDedupeEnabled` and `heatmapQuotaEnabled` are false, or
no `REDIS_URL` is set, `createHeatmapRedis` returns `null` and the
consumer logs:
`Redis disabled (no quota/dedupe or no REDIS_URL)`.

## 7. Failure policy

Redis down with dedupe/quota enabled: today behavior is to surface the
error. Pin down fail-open (skip dedupe/quota; still upload) vs
fail-closed (drop) per business requirement and document here.

## 8. Tests

`heatmap-redis.test.ts` covers key shape + basic ops. Add a "redis
down" scenario test once policy is decided.

## 9. Cross-links

- [kafka-consumer](./kafka-consumer.md)
- [s3-writer](./s3-writer.md)
