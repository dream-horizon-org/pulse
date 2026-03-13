import type { RootCauseMetricKey } from "../../../../hooks/useGetRootCause";

/** Display order and labels for Metric | Value | Baseline | Delta table */
export const ROOT_CAUSE_METRIC_COLUMNS: {
  key: RootCauseMetricKey;
  label: string;
  format?: "number" | "percent" | "ms";
}[] = [
  { key: "apdex", label: "APDEX", format: "number" },
  { key: "error_rate", label: "Error Rate %", format: "percent" },
  { key: "poor_user_pct", label: "Poor User %", format: "percent" },
  { key: "duration_p50", label: "Duration P50 (ms)", format: "ms" },
  { key: "duration_p95", label: "Duration P95 (ms)", format: "ms" },
  { key: "crash_rate", label: "Crash Rate %", format: "percent" },
  { key: "anr_rate", label: "ANR Rate %", format: "percent" },
  { key: "frozen_frame_rate", label: "Frozen Frame Rate %", format: "percent" },
  { key: "slow_frame_rate", label: "Slow Frame Rate %", format: "percent" },
  { key: "volume", label: "Volume", format: "number" },
];

export const ROOT_CAUSE_MESSAGES = {
  LOADING: "Loading root cause analysis…",
  ERROR: "Failed to load root cause analysis",
  NO_DATA: "No data available",
  EVERYTHING_GOOD: "Everything is good — no problematic segments identified",
} as const;
