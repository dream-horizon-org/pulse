import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import {
  isValidIstWallClockString,
} from "../../../utils/DateUtil";
import type { HeatmapLocalFilters } from "./heatmapLocalFilters";
import { canonicalHeatmapBreakpoint } from "./heatmapLocalFilters";
import { heatmapBreakpointDisplayLabel } from "./heatmap.types";
import {
  HEATMAP_QUICK_TIME_PRESETS,
  HEATMAP_TIME_PRESET_CUSTOM,
  inferHeatmapTimePreset,
} from "./heatmapTimePresets";

dayjs.extend(utc);

/** Both bounds set and parse as IST wall-clock — required before heatmap API calls. */
export function isHeatmapTimeRangeQueryReady(
  value: Pick<HeatmapLocalFilters, "startTime" | "endTime">,
): boolean {
  const { startTime, endTime } = value;
  return (
    !!startTime?.trim() &&
    !!endTime?.trim() &&
    isValidIstWallClockString(startTime) &&
    isValidIstWallClockString(endTime)
  );
}

/** IST wall-clock span for custom (non–quick-preset) ranges — empty if invalid. */
export function formatHeatmapCustomDateRangeLabel(
  value: Pick<HeatmapLocalFilters, "startTime" | "endTime">,
): string {
  const { startTime, endTime } = value;
  if (
    !startTime?.trim() ||
    !endTime?.trim() ||
    !isValidIstWallClockString(startTime) ||
    !isValidIstWallClockString(endTime)
  ) {
    return "";
  }
  return `${dayjs(startTime, "YYYY-MM-DD HH:mm:ss").format("MMM D, HH:mm")} – ${dayjs(endTime, "YYYY-MM-DD HH:mm:ss").format("MMM D, HH:mm")}`;
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
  value: Pick<
    HeatmapLocalFilters,
    "platform" | "appVersion" | "region" | "breakpoint"
  >,
): number {
  return [
    value.platform,
    value.appVersion,
    value.region,
    value.breakpoint,
  ].filter((v) => v?.trim()).length;
}

export type HeatmapAudiencePillKey =
  | "platform"
  | "appVersion"
  | "region"
  | "breakpoint";

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
  const bp = value.breakpoint?.trim();
  if (p) {
    out.push({ key: "platform", label: `Platform · ${p}` });
  }
  if (v) {
    out.push({ key: "appVersion", label: `App version · ${v}` });
  }
  if (r) {
    out.push({ key: "region", label: `Region · ${r}` });
  }
  if (bp) {
    const canon = canonicalHeatmapBreakpoint(bp);
    const wire = canon || bp;
    out.push({
      key: "breakpoint",
      label: `Viewport · ${heatmapBreakpointDisplayLabel(wire)}`,
    });
  }
  return out;
}
