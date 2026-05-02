/** Copy aligned with `RootCause.constants` / interaction RCA tone. */
export const SCREEN_ROOT_CAUSE_MESSAGES = {
  NO_DATA_IN_PERIOD:
    "No data available for this screen in the selected period.",
  NO_FRUSTRATION_BODY:
    "Bad frustration count is zero for this screen in the selected period.",
  NO_SEGMENT_BREAKDOWN: "No segment breakdown available for this period.",
} as const;

export const SCREEN_RCA_METRIC_LABELS: Record<string, string> = {
  click_volume: "Click volume",
  tap_count: "Tap count",
  rage_count: "Rage tap count",
  dead_count: "Dead click count",
  bad_frustration: "Bad frustration",
};

export const SCREEN_RCA_MODE_LABELS: Record<string, string> = {
  flat: "Flat",
  hierarchical: "Hierarchical",
};
