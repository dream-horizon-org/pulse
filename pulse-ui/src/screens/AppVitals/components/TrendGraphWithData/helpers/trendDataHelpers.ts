/**
 * Helper functions for trend data processing
 */
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import {
  getTimeBucketSize,
  TimeBucketSize,
  getBucketDurationMs,
} from "../../../../../utils/TimeBucketUtil";
import { COLUMN_NAME } from "../../../../../constants/PulseOtelSemcov";
import { formatTimeToISO } from "../../../../../utils/DateUtil";
import type { StartEndDateTimeType } from "../../../../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";

dayjs.extend(utc);

export function getBucketSize(
  startTime: string,
  endTime: string,
): TimeBucketSize {
  return getTimeBucketSize(startTime, endTime);
}

/** True when the selected range covers more than one UTC calendar day. */
export function trendRangeSpansMultipleUtcDays(
  rangeStart?: string,
  rangeEnd?: string,
): boolean {
  if (!rangeStart?.trim() || !rangeEnd?.trim()) return false;
  const start = dayjs.utc(rangeStart);
  const end = dayjs.utc(rangeEnd);
  if (!start.isValid() || !end.isValid()) return false;
  return !start.isSame(end, "day");
}

/**
 * Trend axis labels — same tokens as Home dashboard charts
 * (see UserEngagementGraph / ActiveSessionsGraph: "MMM DD", "MMM DD, HH:mm").
 */
export function formatTrendDate(
  timestamp: string,
  bucketSize: TimeBucketSize,
): string {
  const d = dayjs.utc(timestamp);

  // Sub-day buckets: datetime with comma (matches Home tooltips)
  if (
    bucketSize === "1m" ||
    bucketSize === "5m" ||
    bucketSize === "10m" ||
    bucketSize === "30m" ||
    bucketSize === "1h" ||
    bucketSize === "3h" ||
    bucketSize === "6h" ||
    bucketSize === "12h"
  ) {
    return d.format("MMM DD, HH:mm");
  }

  if (bucketSize === "1d" || bucketSize === "3d") {
    return d.format("MMM DD");
  }

  if (bucketSize === "1w" || bucketSize === "2w" || bucketSize === "4w") {
    return d.format("MMM DD");
  }

  if (bucketSize === "1M" || bucketSize === "3M" || bucketSize === "6M") {
    return d.format("MMM YYYY");
  }

  if (bucketSize === "1y") {
    return d.format("MMM YYYY");
  }

  return d.format("MMM DD");
}

/** Normalize API bucket timestamp to ISO for chart category values (brush selection). */
export function normalizeTrendBucketTime(raw: unknown): string {
  if (raw == null) return "";
  return formatTimeToISO(String(raw));
}

/**
 * Map a brush from first/last bucket starts to the global time filter shape.
 * End time is inclusive of the last bucket (end of bucket − 1 ms).
 */
export function trendBrushSelectionToTimeFilter(
  firstBucketStartIso: string,
  lastBucketStartIso: string,
  bucketSize: TimeBucketSize,
): StartEndDateTimeType {
  const ms = getBucketDurationMs(bucketSize);
  const endUtc = dayjs
    .utc(lastBucketStartIso)
    .add(ms, "millisecond")
    .subtract(1, "millisecond");
  return {
    startDate: dayjs.utc(firstBucketStartIso).format("YYYY-MM-DD HH:mm:ss"),
    endDate: endUtc.format("YYYY-MM-DD HH:mm:ss"),
  };
}

export function buildCommonFilters(
  appVersion?: string,
  osVersion?: string,
  device?: string,
  platform?: string,
  networkProvider?: string,
  state?: string,
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

  if (platform && platform !== "all") {
    filterArray.push({
      field: COLUMN_NAME.PLATFORM,
      operator: "EQ" as const,
      value: [platform],
    });
  }

  if (networkProvider && networkProvider !== "all") {
    filterArray.push({
      field: COLUMN_NAME.NETWORK_PROVIDER,
      operator: "EQ" as const,
      value: [networkProvider],
    });
  }

  if (state && state !== "all") {
    filterArray.push({
      field: COLUMN_NAME.STATE,
      operator: "EQ" as const,
      value: [state],
    });
  }

  return filterArray;
}
