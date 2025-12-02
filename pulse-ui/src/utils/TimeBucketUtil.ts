/**
 * Utility functions for determining time bucket sizes based on time ranges
 * Used across the application for consistent time bucketing in graphs
 */

/**
 * Union type for all supported time bucket sizes
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
  | "1d"
  // | "3d"
  // | "1w"
  // | "2w"
  // | "4w"
  // | "1M"
  // | "3M"
  // | "6M"
  // | "1y";

/**
 * Bucket size definitions in milliseconds
 */
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
  // "3d": 3 * 24 * 60 * 60 * 1000,
  // "1w": 7 * 24 * 60 * 60 * 1000,
  // "2w": 14 * 24 * 60 * 60 * 1000,
  // "4w": 28 * 24 * 60 * 60 * 1000,
  // "1M": 30 * 24 * 60 * 60 * 1000,
  // "3M": 90 * 24 * 60 * 60 * 1000,
  // "6M": 180 * 24 * 60 * 60 * 1000,
  // "1y": 365 * 24 * 60 * 60 * 1000,
};

/**
 * Ordered list of bucket sizes from smallest to largest
 */
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
  // "3d",
  // "1w",
  // "2w",
  // "4w",
  // "1M",
  // "3M",
  // "6M",
  // "1y",
];

const MAX_POINTS = 20;
const MIN_BUCKET_SIZE_MS = 1 * 60 * 1000; // 1 minutes
const MAX_TIME_RANGE_MS = 90 * 24 * 60 * 60 * 1000; // 90 days

/**
 * Determines the appropriate time bucket size based on the time range
 * Ensures maximum 20 points, minimum 5 minute buckets, and maximum 90 day range
 * @param startTime - ISO string or date string start time
 * @param endTime - ISO string or date string end time
 * @returns Time bucket size from the union type
 */
export function getTimeBucketSize(
  startTime: string,
  endTime: string,
): TimeBucketSize {
  if (!startTime || !endTime) return "5m";

  const start = new Date(startTime).getTime();
  const end = new Date(endTime).getTime();
  let diffMs = end - start;

  // Clamp to maximum 90 days
  if (diffMs > MAX_TIME_RANGE_MS) {
    diffMs = MAX_TIME_RANGE_MS;
  }

  // Calculate ideal bucket size to get exactly MAX_POINTS
  const idealBucketSizeMs = diffMs / MAX_POINTS;

  // Ensure minimum bucket size is 5 minutes
  const requiredBucketSizeMs = Math.max(idealBucketSizeMs, MIN_BUCKET_SIZE_MS);

  // Find the smallest bucket size that is >= requiredBucketSizeMs
  for (const bucketSize of BUCKET_ORDER) {
    if (BUCKET_SIZES_MS[bucketSize] >= requiredBucketSizeMs) {
      return bucketSize;
    }
  }

  // Fallback to largest bucket if needed
  return "3h";
}
