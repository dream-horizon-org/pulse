/**
 * Mirrors pulse-ui/src/utils/TimeBucketUtil.ts for interaction metric time-series bucketing.
 */

export type TimeBucketSize =
  | "1m"
  | "5m"
  | "10m"
  | "30m"
  | "1h"
  | "3h"
  | "6h"
  | "12h"
  | "1d";

const BUCKET_SIZES_MS: Record<TimeBucketSize, number> = {
  "1m": 1 * 60 * 1000,
  "5m": 5 * 60 * 1000,
  "10m": 10 * 60 * 1000,
  "30m": 30 * 60 * 1000,
  "1h": 1 * 60 * 60 * 1000,
  "3h": 3 * 60 * 60 * 1000,
  "6h": 6 * 60 * 60 * 1000,
  "12h": 12 * 60 * 60 * 1000,
  "1d": 1 * 24 * 60 * 60 * 1000,
};

const BUCKET_ORDER: TimeBucketSize[] = [
  "1m",
  "5m",
  "10m",
  "30m",
  "1h",
  "3h",
  "6h",
  "12h",
  "1d",
];

const MAX_POINTS = 50;
const MIN_BUCKET_SIZE_MS = 1 * 60 * 1000;
const MAX_TIME_RANGE_MS = 90 * 24 * 60 * 60 * 1000;

export function getTimeBucketSize(
  startTime: string,
  endTime: string,
): TimeBucketSize {
  if (!startTime || !endTime) return "1m";

  const start = new Date(startTime).getTime();
  const end = new Date(endTime).getTime();
  let diffMs = end - start;

  if (diffMs > MAX_TIME_RANGE_MS) {
    diffMs = MAX_TIME_RANGE_MS;
  }

  const idealBucketSizeMs = diffMs / MAX_POINTS;
  const requiredBucketSizeMs = Math.max(idealBucketSizeMs, MIN_BUCKET_SIZE_MS);

  for (const bucketSize of BUCKET_ORDER) {
    if (BUCKET_SIZES_MS[bucketSize] >= requiredBucketSizeMs) {
      return bucketSize;
    }
  }

  return "1d";
}
