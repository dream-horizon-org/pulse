import type { CriticalInteractionDetailsFilterValues } from "../../CriticalInteractionDetails/CriticalInteractionDetails.interface";

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

/** Args for `useHeatmapData` (regions + optional viewport breakpoint). */
export function heatmapFiltersToRequestArgs(f: HeatmapLocalFilters) {
  return {
    startTime: f.startTime,
    endTime: f.endTime,
    app_version: f.appVersion.trim() || undefined,
    platform: f.platform.trim() || undefined,
    region: f.region.trim() || undefined,
    breakpoint: f.breakpoint.trim() || undefined,
  };
}
