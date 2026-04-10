import type { CriticalInteractionDetailsFilterValues } from "../../CriticalInteractionDetails/CriticalInteractionDetails.interface";
import {
  HEATMAP_BREAKPOINT_VALUES,
  LEGACY_HEATMAP_BREAKPOINT_TO_API,
  type HeatmapBreakpoint,
} from "./heatmap.types";

/** Heatmap-only filter state; mirrors page filters until the user edits or resets. */
export type HeatmapLocalFilters = {
  startTime: string;
  endTime: string;
  platform: string;
  appVersion: string;
  /** Pulse dashboard `STATE` dimension; sent as heatmap API `region`. */
  region: string;
  /** Viewport bucket; sent as `breakpoint` when set. */
  breakpoint: string;
};

export function defaultHeatmapLocalFilters(
  filterValues: CriticalInteractionDetailsFilterValues | undefined,
  pageStartTime: string,
  pageEndTime: string,
): HeatmapLocalFilters {
  return {
    startTime: pageStartTime || "",
    endTime: pageEndTime || "",
    platform: filterValues?.PLATFORM?.trim() ?? "",
    appVersion: filterValues?.APP_VERSION?.trim() ?? "",
    region: filterValues?.STATE?.trim() ?? "",
    breakpoint: "",
  };
}

export function heatmapLocalFiltersMatchPage(
  local: HeatmapLocalFilters,
  filterValues: CriticalInteractionDetailsFilterValues | undefined,
  pageStartTime: string,
  pageEndTime: string,
): boolean {
  const page = defaultHeatmapLocalFilters(
    filterValues,
    pageStartTime,
    pageEndTime,
  );
  return (
    local.startTime === page.startTime &&
    local.endTime === page.endTime &&
    local.platform === page.platform &&
    local.appVersion === page.appVersion &&
    local.region === page.region &&
    local.breakpoint === page.breakpoint
  );
}

function normalizeBreakpointForApi(raw: string): string | undefined {
  const b = raw.trim();
  if (!b) return undefined;
  if ((HEATMAP_BREAKPOINT_VALUES as readonly string[]).includes(b)) {
    return b;
  }
  const mapped = LEGACY_HEATMAP_BREAKPOINT_TO_API[b];
  if (mapped) return mapped;
  return b;
}

/** Args for `useHeatmapData` (regions + optional viewport breakpoint). */
export function heatmapFiltersToRequestArgs(f: HeatmapLocalFilters) {
  return {
    startTime: f.startTime,
    endTime: f.endTime,
    app_version: f.appVersion.trim() || undefined,
    platform: f.platform.trim() || undefined,
    region: f.region.trim() || undefined,
    breakpoint: normalizeBreakpointForApi(f.breakpoint),
  };
}

/** Maps a stored filter value to canonical `HeatmapBreakpoint` when possible (e.g. legacy keys). */
export function canonicalHeatmapBreakpoint(
  raw: string | undefined,
): HeatmapBreakpoint | "" {
  const b = raw?.trim() ?? "";
  if (!b) return "";
  if ((HEATMAP_BREAKPOINT_VALUES as readonly string[]).includes(b)) {
    return b as HeatmapBreakpoint;
  }
  return LEGACY_HEATMAP_BREAKPOINT_TO_API[b] ?? "";
}
