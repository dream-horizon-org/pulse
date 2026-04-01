import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import {
  getDateFromUTCTimeString,
  isValidUtcWallClockString,
} from "../../../utils/DateUtil";
import type { HeatmapLocalFilters } from "./heatmapLocalFilters";

dayjs.extend(utc);

export function formatHeatmapTimeButtonLabel(
  value: Pick<HeatmapLocalFilters, "startTime" | "endTime">,
): string {
  const { startTime, endTime } = value;
  if (
    !startTime?.trim() ||
    !endTime?.trim() ||
    !isValidUtcWallClockString(startTime) ||
    !isValidUtcWallClockString(endTime)
  ) {
    return "Time range";
  }
  const startDate = getDateFromUTCTimeString(startTime);
  const endDate = getDateFromUTCTimeString(endTime);
  if (!startDate || !endDate) {
    return "Time range";
  }
  return `${dayjs.utc(startTime, "YYYY-MM-DD HH:mm:ss").format("MMM D, HH:mm")} – ${dayjs.utc(endTime, "YYYY-MM-DD HH:mm:ss").format("MMM D, HH:mm")}`;
}

export function countHeatmapAudienceFilters(
  value: Pick<HeatmapLocalFilters, "platform" | "appVersion" | "region">,
): number {
  return [value.platform, value.appVersion, value.region].filter((v) =>
    v?.trim(),
  ).length;
}
