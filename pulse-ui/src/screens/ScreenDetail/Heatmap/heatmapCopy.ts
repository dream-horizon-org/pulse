/**
 * User-visible copy for the screen-detail heatmap tab (loading, empty, compare).
 */

export const HEATMAP_COPY_LOADING_HEATMAP = "Loading heatmap…";

export const HEATMAP_COPY_INVALID_TIME_TITLE = "Time range incomplete";

export const HEATMAP_COPY_INVALID_TIME_BODY =
  "Set both From and To in the heatmap time filter, or choose a quick range. Metrics and the map load once the range is valid.";

export const HEATMAP_COPY_LOADING_COMPARISON = "Loading comparison…";

export const HEATMAP_COPY_LOADING_METRICS = "Loading metrics…";

export const HEATMAP_COPY_EMPTY_TITLE = "No heatmap data for this screen";

const HEATMAP_COPY_EMPTY_BODY_GENERIC =
  "We didn’t find any taps or frustration in this range. Try a wider time range or different filters.";

/** Curly quotes match product typography elsewhere in this feature. */
export function heatmapCopyEmptyBody(labelForScreen: string | undefined): string {
  if (!labelForScreen?.trim()) return HEATMAP_COPY_EMPTY_BODY_GENERIC;
  return `We didn’t find any taps or frustration in this range for “${labelForScreen.trim()}”. Try a wider time range or different filters.`;
}

export const HEATMAP_COPY_SUMMARY_TITLE = "Summary";

export const HEATMAP_COPY_SUMMARY_FILTERS_HINT =
  "Filters and time range match the rest of this screen.";

export const HEATMAP_COPY_METRIC_EVENTS = "Events (heatmap scope)";
export const HEATMAP_COPY_METRIC_SESSIONS = "Sessions";
export const HEATMAP_COPY_METRIC_USERS = "Users";
export const HEATMAP_COPY_METRIC_AVG_TIME = "Avg. time";

export const HEATMAP_COPY_COMPARE_SCREENS = "Compare screens";

export const HEATMAP_COPY_COMPARE_MODE_TITLE = "Compare screens";

export const HEATMAP_COPY_EXIT_COMPARE = "Exit compare";

export const HEATMAP_COPY_CURRENT_SCREEN = "Current screen";

export const HEATMAP_COPY_COMPARE_TO_SCREEN = "Compare to screen";

export const HEATMAP_COPY_COMPARE_TO_PLACEHOLDER = "Choose a screen";

export const HEATMAP_COPY_SCREEN_B_FALLBACK = "Screen B";

export const HEATMAP_COPY_SECTION_SCREEN_A = "Screen A";

export const HEATMAP_COPY_SECTION_SCREEN_B = "Screen B";

export const HEATMAP_COPY_RETRY = "Retry";

export const HEATMAP_COPY_METRICS_BLOCKED_BEFORE =
  "Metrics load with the heatmap preview. Use ";

export const HEATMAP_COPY_METRICS_BLOCKED_AFTER = " in the column above.";
