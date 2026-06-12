import type { ScreenRootCauseMode } from "../../../../hooks/useGetScreenRootCause";

/** Copy aligned with `RootCause.constants` / interaction RCA tone. */
export const SCREEN_ROOT_CAUSE_MESSAGES = {
  NO_DATA_IN_PERIOD:
    "No data available for this screen in the selected period.",
  NO_FRUSTRATION_BODY:
    "Bad frustration count is zero for this screen in the selected period.",
  NO_SEGMENT_BREAKDOWN: "No segment breakdown available for this period.",
} as const;

export const SCREEN_RCA_METRIC_LABELS: Record<string, string> = {
  click_volume: "Total Clicks",
  tap_count: "Good clicks",
  rage_count: "Rage clicks",
  dead_count: "Dead clicks",
  bad_frustration: "Bad frustration clicks",
  bad_frustration_percentage: "Bad frustration %",
};

/** Shown on the metric-row info icon (bad_frustration = dead_count + rage_count). */
export const SCREEN_RCA_BAD_FRUSTRATION_PERCENTAGE_TOOLTIP =
  "Bad frustration % = (bad frustration clicks ÷ total clicks) × 100. " +
  "A click is either a dead click or a rage tap (not both). Bad frustration = dead clicks + rage clicks.";

export const SCREEN_RCA_MODE_LABELS = {
  flat: "Flat",
  hierarchical: "Hierarchical",
  hybrid: "Hybrid",
} as const satisfies Record<ScreenRootCauseMode, string>;
