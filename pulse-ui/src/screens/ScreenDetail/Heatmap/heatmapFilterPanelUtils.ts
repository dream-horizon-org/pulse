import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import {
  getDateFromUTCTimeString,
  isValidUtcWallClockString,
} from "../../../utils/DateUtil";
import type { HeatmapLocalFilters } from "./heatmapLocalFilters";
import {
  HEATMAP_QUICK_TIME_PRESETS,
  HEATMAP_TIME_PRESET_CUSTOM,
  inferHeatmapTimePreset,
} from "./heatmapTimePresets";

dayjs.extend(utc);

/** UTC wall-clock span for custom (non–quick-preset) ranges — empty if invalid. */
export function formatHeatmapCustomDateRangeLabel(
  value: Pick<HeatmapLocalFilters, "startTime" | "endTime">,
): string {
  const { startTime, endTime } = value;
  if (
    !startTime?.trim() ||
    !endTime?.trim() ||
    !isValidUtcWallClockString(startTime) ||
    !isValidUtcWallClockString(endTime)
  ) {
    return "";
  }
  const startDate = getDateFromUTCTimeString(startTime);
  const endDate = getDateFromUTCTimeString(endTime);
  if (!startDate || !endDate) {
    return "";
  }
  return `${dayjs.utc(startTime, "YYYY-MM-DD HH:mm:ss").format("MMM D, HH:mm")} – ${dayjs.utc(endTime, "YYYY-MM-DD HH:mm:ss").format("MMM D, HH:mm")}`;
}

/** Clock label: quick-preset name when it matches; otherwise the date span. */
export function formatHeatmapTimeButtonLabel(
  value: Pick<HeatmapLocalFilters, "startTime" | "endTime">,
): string {
  const presetKey = inferHeatmapTimePreset(value.startTime, value.endTime);
  if (presetKey !== HEATMAP_TIME_PRESET_CUSTOM) {
    const preset = HEATMAP_QUICK_TIME_PRESETS.find((p) => p.value === presetKey);
    if (preset) {
      return preset.label;
    }
  }
  const range = formatHeatmapCustomDateRangeLabel(value);
  return range || "Time range";
}

export function countHeatmapAudienceFilters(
  value: Pick<HeatmapLocalFilters, "platform" | "appVersion" | "region">,
): number {
  return [value.platform, value.appVersion, value.region].filter((v) =>
    v?.trim(),
  ).length;
}

export type HeatmapAudiencePillKey = "platform" | "appVersion" | "region";

export type HeatmapAudiencePillEntry = {
  key: HeatmapAudiencePillKey;
  label: string;
};

/** Non-empty audience dimensions as pill labels (Platform / App version / Region). */
export function getHeatmapAudiencePillEntries(
  value: Pick<HeatmapLocalFilters, HeatmapAudiencePillKey>,
): HeatmapAudiencePillEntry[] {
  const out: HeatmapAudiencePillEntry[] = [];
  const p = value.platform?.trim();
  const v = value.appVersion?.trim();
  const r = value.region?.trim();
  if (p) {
    out.push({ key: "platform", label: `Platform · ${p}` });
  }
  if (v) {
    out.push({ key: "appVersion", label: `App version · ${v}` });
  }
  if (r) {
    out.push({ key: "region", label: `Region · ${r}` });
  }
  return out;
}
