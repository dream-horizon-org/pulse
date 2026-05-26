/** Mirrors pulse_ai ScreenRcaMetrics (snake_case — passed through as-is). */
export interface ScreenRcaMetricsV2 {
  affected_volume?: number | null;
  rate?: string | null;
  p50_ms?: number | null;
  p95_ms?: number | null;
  // bad_clicks only — raw counts from otel_logs
  click_volume?: number | null;
  rage_count?: number | null;
  dead_count?: number | null;
}

/** Mirrors pulse_ai ScreenRcaSpecificIssue (snake_case). */
export interface ScreenRcaSpecificIssueV2 {
  group_id?: string | null;
  issue?: string | null;
  count?: number | null;
  avg_duration_ms?: number | null;
  thread_name?: string | null;
}

/** Mirrors pulse_ai ScreenRcaProblem (snake_case — passed through as-is). */
export interface ScreenRcaProblemV2 {
  problem_type: string;
  rank: number;
  weightage: number;
  most_affected_segment?: string | null;
  metrics?: ScreenRcaMetricsV2 | null;  // baseline (overall screen)
  segment_metrics?: ScreenRcaMetricsV2 | null;  // value (most-affected segment)
  specific_issues?: ScreenRcaSpecificIssueV2[] | null;
  metric_id?: string | null;
}

/** Mirrors pulse_ai ScreenRcaEvidences (snake_case). */
export interface ScreenRcaEvidencesV2 {
  sessions?: string[] | null;
  heatmap_available?: boolean;
}

/** Mirrors backend `ScreenRcaV2Response`. */
export interface ScreenRcaV2Data {
  problems?: ScreenRcaProblemV2[] | null;
  evidences?: ScreenRcaEvidencesV2 | null;
}

export interface UseGetScreenRootCauseV2Params {
  screenName: string | null | undefined;
  /** Exclusive upper bound on event timestamps (ISO-8601). Server window = 7-day RCA lookback ending at windowEnd. */
  windowEndIso: string | null | undefined;
  projectId: string | null | undefined;
  enabled?: boolean;
}
