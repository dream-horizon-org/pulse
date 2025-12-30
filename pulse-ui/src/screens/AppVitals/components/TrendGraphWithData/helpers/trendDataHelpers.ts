/**
 * Helper functions for trend data processing
 */
import dayjs from "dayjs";
import {
  getTimeBucketSize,
  TimeBucketSize,
} from "../../../../../utils/TimeBucketUtil";
import { COLUMN_NAME } from "../../../../../constants/PulseOtelSemcov";
export function getBucketSize(
  startTime: string,
  endTime: string,
): TimeBucketSize {
  return getTimeBucketSize(startTime, endTime);
}

export function formatTrendDate(
  timestamp: string,
  bucketSize: TimeBucketSize,
): string {
  // Minutes: show time with minutes
  if (
    bucketSize === "1m" ||
    bucketSize === "5m" ||
    bucketSize === "10m" ||
    bucketSize === "30m"
  ) {
    return dayjs(timestamp).format("HH:mm");
  }

  // Hours: show hour with AM/PM
  if (
    bucketSize === "1h" ||
    bucketSize === "3h" ||
    bucketSize === "6h" ||
    bucketSize === "12h"
  ) {
    return dayjs(timestamp).format("h A");
  }

  // Days: show month and day
  if (bucketSize === "1d" || bucketSize === "3d") {
    return dayjs(timestamp).format("MMM D");
  }

  // Weeks: show month and day
  if (bucketSize === "1w" || bucketSize === "2w" || bucketSize === "4w") {
    return dayjs(timestamp).format("MMM D");
  }

  // Months: show month and year
  if (bucketSize === "1M" || bucketSize === "3M" || bucketSize === "6M") {
    return dayjs(timestamp).format("MMM YYYY");
  }

  // Year: show month and year
  if (bucketSize === "1y") {
    return dayjs(timestamp).format("MMM YYYY");
  }

  // Fallback
  return dayjs(timestamp).format("MMM D");
}

export function buildCommonFilters(
  appVersion?: string,
  osVersion?: string,
  device?: string,
) {
  const filterArray = [];

  if (appVersion && appVersion !== "all") {
    filterArray.push({
      field: COLUMN_NAME.APP_VERSION,
      operator: "EQ" as const,
      value: [appVersion],
    });
  }

  if (osVersion && osVersion !== "all") {
    filterArray.push({
      field: "OsVersion",
      operator: "EQ" as const,
      value: [osVersion],
    });
  }

  if (device && device !== "all") {
    filterArray.push({
      field: "DeviceModel",
      operator: "EQ" as const,
      value: [device],
    });
  }

  return filterArray;
}
