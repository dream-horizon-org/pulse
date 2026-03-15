/** Display label for metric keys in segment tables. Backend sends keys as-is; this maps to readable labels. */
export const ROOT_CAUSE_METRIC_LABELS: Record<string, string> = {
  apdex: "APDEX",
  error_rate: "Error Rate",
  poor_user_pct: "Poor User %",
  duration_p50: "Duration P50",
  duration_p95: "Duration P95",
  crash_rate: "Crash Rate",
  anr_rate: "ANR Rate",
  frozen_frame_rate: "Frozen Frame Rate",
  slow_frame_rate: "Slow Frame Rate",
  volume: "Volume",
};

/** Metric keys in display order (missing keys are appended after). */
export const ROOT_CAUSE_METRIC_ORDER = [
  "apdex",
  "error_rate",
  "poor_user_pct",
  "duration_p50",
  "duration_p95",
  "crash_rate",
  "anr_rate",
  "frozen_frame_rate",
  "slow_frame_rate",
  "volume",
];

export const ROOT_CAUSE_MESSAGES = {
  EVERYTHING_GOOD:
    "No significant issues detected for this period. Metrics look good.",
  NO_DATA: "No data available for this interaction in the selected period.",
  FEATURE_OR_NO_DATA:
    "Root cause analysis is not available. The feature may be disabled or there is no data for this period.",
  REQUEST_TIMEOUT:
    "Request timed out. Root cause computation can take up to a minute. Please try again.",
} as const;
