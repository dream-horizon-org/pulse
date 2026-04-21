import { createHash } from "crypto";

import { DateTime } from "luxon";
import Redis from "ioredis";

import { sanitizePathSegment } from "./s3-key";
import type { Config } from "./config";

/**
 * Quota key dimensions align with S3 path segments (UTC date, then project → screen → platform → app version → breakpoint).
 * All quota decisions use EVAL on the primary; no split GET + INCR across round trips.
 */
export function buildHeatmapQuotaKey(params: {
  metaTimestampMs: number;
  projectId: string;
  screenHref: string;
  platform: string;
  appVersionLabel: string;
  breakpoint: string;
}): string {
  const dateUtc = DateTime.fromMillis(params.metaTimestampMs, {
    zone: "utc",
  }).toFormat("yyyyMMdd");

  const project = sanitizePathSegment(params.projectId, "unknown");
  const screen = sanitizePathSegment(params.screenHref, "screen");
  const plat = sanitizePathSegment(params.platform, "unknown");
  const ver = sanitizePathSegment(params.appVersionLabel, "unknown");
  const bp = sanitizePathSegment(params.breakpoint, "unknown");

  return `heatmap:quota:${dateUtc}:${project}:${screen}:${plat}:${ver}:${bp}`;
}

/** Dedupe before quota so Kafka replays do not burn slots. */
export function buildHeatmapDedupeKey(params: {
  sessionId: string;
  screenHref: string;
  metaTimestamp: number;
  base64: string;
}): string {
  const screen = sanitizePathSegment(params.screenHref, "screen");
  const hash = createHash("sha256")
    .update(params.base64, "utf8")
    .digest("hex")
    .slice(0, 16);
  return `heatmap:dedupe:${params.sessionId}:${screen}:${params.metaTimestamp}:${hash}`;
}

/** Reserve one upload slot if count < limit; refresh TTL. Returns [allowed, newCount]. */
const LUA_RESERVE_QUOTA = `
local limit = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])
local n = tonumber(redis.call('GET', KEYS[1])) or 0
if n >= limit then
  return {0, n}
end
local new = redis.call('INCR', KEYS[1])
redis.call('EXPIRE', KEYS[1], ttl)
return {1, new}
`;

/** Release one reserved slot after a failed S3 upload (clamp at 0). */
const LUA_RELEASE_QUOTA = `
local n = tonumber(redis.call('GET', KEYS[1])) or 0
if n <= 0 then
  return 0
end
return redis.call('DECR', KEYS[1])
`;

export class HeatmapRedis {
  private client: Redis;

  constructor(
    redisUrl: string,
    private readonly quotaTtlSec: number,
    private readonly dedupeTtlSec: number,
  ) {
    this.client = new Redis(redisUrl, {
      maxRetriesPerRequest: 2,
      enableReadyCheck: true,
    });
  }

  public async ping(): Promise<void> {
    await this.client.ping();
  }

  public async quit(): Promise<void> {
    await this.client.quit();
  }

  /** @returns true if this is the first claim (should process), false if duplicate. */
  public async tryClaimDedupe(dedupeKey: string): Promise<boolean> {
    const r = await this.client.set(dedupeKey, "1", "EX", this.dedupeTtlSec, "NX");
    return r === "OK";
  }

  /** Reserve quota; on S3 failure call releaseQuota with the same key. */
  public async reserveQuota(quotaKey: string, limit: number): Promise<{
    allowed: boolean;
    count: number;
  }> {
    const result = (await this.client.eval(
      LUA_RESERVE_QUOTA,
      1,
      quotaKey,
      String(limit),
      String(this.quotaTtlSec),
    )) as [number, number];

    const allowed = result[0] === 1;
    const count = result[1];
    return { allowed, count };
  }

  public async releaseQuota(quotaKey: string): Promise<void> {
    await this.client.eval(LUA_RELEASE_QUOTA, 1, quotaKey);
  }
}

export function createHeatmapRedis(config: Config): HeatmapRedis | null {
  if (!config.redisUrl) {
    return null;
  }
  if (!config.heatmapQuotaEnabled && !config.heatmapDedupeEnabled) {
    return null;
  }
  return new HeatmapRedis(
    config.redisUrl,
    config.heatmapQuotaTtlSeconds,
    config.heatmapDedupeTtlSeconds,
  );
}
