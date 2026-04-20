import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { CRITICAL_INTERACTION_QUICK_TIME_FILTERS } from "../../../constants";
import { getStartAndEndDateTimeString } from "../../../utils/DateUtil";

dayjs.extend(utc);

/** Same margin before “now” as screen-level DateTimeRangePicker. */
export const HEATMAP_TIME_RANGE_SUBTRACT_MINUTES = 2;

export const HEATMAP_TIME_PRESET_CUSTOM = "custom";

export const HEATMAP_QUICK_TIME_PRESETS = [
  {
    label: "Last 24 hours",
    value: CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_24_HOURS,
  },
  {
    label: "Last 7 days",
    value: CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_7_DAYS,
  },
  {
    label: "Last 30 days",
    value: CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_30_DAYS,
  },
  {
    label: "Last 90 days",
    value: CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_90_DAYS,
  },
] as const;

export function inferHeatmapTimePreset(
  startTime: string,
  endTime: string,
): string {
  const sub = HEATMAP_TIME_RANGE_SUBTRACT_MINUTES;
  const expectedEnd = dayjs.utc().subtract(sub, "minute");
  const end = dayjs.utc(endTime, "YYYY-MM-DD HH:mm:ss");
  if (!end.isValid()) {
    return HEATMAP_TIME_PRESET_CUSTOM;
  }
  if (Math.abs(end.diff(expectedEnd, "minute")) > 3) {
    return HEATMAP_TIME_PRESET_CUSTOM;
  }

  const start = dayjs.utc(startTime, "YYYY-MM-DD HH:mm:ss");
  if (!start.isValid()) {
    return HEATMAP_TIME_PRESET_CUSTOM;
  }

  for (const preset of HEATMAP_QUICK_TIME_PRESETS) {
    const { startDate } = getStartAndEndDateTimeString(preset.value, sub);
    const expectedStart = dayjs.utc(startDate, "YYYY-MM-DD HH:mm:ss");
    if (Math.abs(start.diff(expectedStart, "minute")) <= 3) {
      return preset.value;
    }
  }

  return HEATMAP_TIME_PRESET_CUSTOM;
}
