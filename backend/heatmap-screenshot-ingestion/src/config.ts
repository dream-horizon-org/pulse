export interface Config {
  kafkaBrokers: string;
  kafkaTopic: string;
  kafkaGroupId: string;
  s3Endpoint: string;
  s3Region: string;
  s3Bucket: string;
  /** First path segment(s) before project id, e.g. `heatmap-screenshots` */
  s3Prefix: string;
  s3AccessKeyId?: string;
  s3SecretAccessKey?: string;
  fetchBatchSize: number;
  schemaVersion: number;

  /**
   * From `REDIS_URL`, or `redis://REDIS_HOST:REDIS_PORT` when host is set.
   * Point at external Redis (e.g. pulse-kong) in deploy `.env`.
   */
  redisUrl: string;
  /** Daily uploads per quota key (default 5). */
  heatmapQuotaPerDay: number;
  /** TTL for quota keys (default 48h). Refreshed on each successful reserve. */
  heatmapQuotaTtlSeconds: number;
  /** When true, enforce cap via Redis Lua on primary (needs resolved Redis URL). */
  heatmapQuotaEnabled: boolean;
  /** SETNX dedupe before quota (optional; needs resolved Redis URL). */
  heatmapDedupeEnabled: boolean;
  heatmapDedupeTtlSeconds: number;
}

function parseBool(
  raw: string | undefined,
  defaultValue: boolean,
): boolean {
  if (raw === undefined || raw === "") {
    return defaultValue;
  }
  const v = raw.trim().toLowerCase();
  if (v === "true" || v === "1" || v === "yes") {
    return true;
  }
  if (v === "false" || v === "0" || v === "no") {
    return false;
  }
  return defaultValue;
}

/** Prefer `REDIS_URL`; else build from `REDIS_HOST` + `REDIS_PORT` (e.g. pulse-kong Redis). */
export function resolveRedisUrlFromEnv(
  env: NodeJS.ProcessEnv = process.env,
): string {
  const explicit = (env.REDIS_URL || "").trim();
  if (explicit.length > 0) {
    return explicit;
  }
  const host = (env.REDIS_HOST || "").trim();
  const port = (env.REDIS_PORT || "6379").trim();
  if (host.length === 0) {
    return "";
  }
  return `redis://${host}:${port}`;
}

export function loadConfig(): Config {
  const redisUrl = resolveRedisUrlFromEnv();
  const hasRedisUrl = redisUrl.length > 0;

  const s3Endpoint =
    process.env.HEATMAP_S3_ENDPOINT ||
    process.env.S3_ENDPOINT ||
    "http://localhost:9000";
  const s3Region =
    process.env.HEATMAP_S3_REGION || process.env.S3_REGION || "us-east-1";
  const s3Bucket =
    process.env.HEATMAP_S3_BUCKET ||
    process.env.S3_BUCKET ||
    "heatmap-assets";
  const s3AccessKeyId =
    process.env.HEATMAP_S3_ACCESS_KEY_ID || process.env.S3_ACCESS_KEY_ID;
  const s3SecretAccessKey =
    process.env.HEATMAP_S3_SECRET_ACCESS_KEY ||
    process.env.S3_SECRET_ACCESS_KEY;

  return {
    kafkaBrokers: process.env.KAFKA_BROKERS || "localhost:9092",
    kafkaTopic: process.env.KAFKA_TOPIC || "session_recording_events",
    kafkaGroupId:
      process.env.KAFKA_GROUP_ID || "heatmap-screenshot-ingestion",
    s3Endpoint,
    s3Region,
    s3Bucket,
    s3Prefix: process.env.S3_PREFIX || "heatmap-screenshots",
    s3AccessKeyId,
    s3SecretAccessKey,
    fetchBatchSize: parseInt(process.env.FETCH_BATCH_SIZE || "100", 10),
    schemaVersion: parseInt(process.env.HEATMAP_JSON_SCHEMA_VERSION || "1", 10),

    redisUrl,
    heatmapQuotaPerDay: parseInt(process.env.HEATMAP_QUOTA_PER_DAY || "5", 10),
    heatmapQuotaTtlSeconds: parseInt(
      process.env.HEATMAP_QUOTA_TTL_SECONDS || "172800",
      10,
    ),
    heatmapQuotaEnabled: parseBool(
      process.env.HEATMAP_QUOTA_ENABLED,
      hasRedisUrl,
    ),
    heatmapDedupeEnabled: parseBool(
      process.env.HEATMAP_DEDUPE_ENABLED,
      false,
    ),
    heatmapDedupeTtlSeconds: parseInt(
      process.env.HEATMAP_DEDUPE_TTL_SECONDS || "172800",
      10,
    ),
  };
}
