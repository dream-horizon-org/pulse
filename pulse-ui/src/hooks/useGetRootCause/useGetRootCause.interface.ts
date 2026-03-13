/**
 * Root Cause API response types (GET /v1/interactions/:name/root-cause).
 * Baseline and segment metrics use the same key set.
 */
export type RootCauseMetricKey =
  | "volume"
  | "apdex"
  | "error_rate"
  | "poor_user_pct"
  | "duration_p50"
  | "duration_p95"
  | "crash_rate"
  | "anr_rate"
  | "frozen_frame_rate"
  | "slow_frame_rate";

export type RootCauseMetrics = Partial<Record<RootCauseMetricKey, number>>;

export type RootCauseSegment = {
  label: string;
  dimensions?: Record<string, string>;
  metrics: RootCauseMetrics;
  deltas?: Partial<Record<RootCauseMetricKey, number>>;
};

export type RootCauseResponse = {
  baseline: RootCauseMetrics;
  segments: RootCauseSegment[];
  cachedAt?: string;
  mode?: "hierarchical" | "flat";
  /** When true, no problematic segments (everything is good) */
  everythingGood?: boolean;
  /** Message when no data or everything good */
  message?: string;
};

export type UseGetRootCauseParams = {
  interactionName: string | null | undefined;
  date?: string | null;
  enabled?: boolean;
};
