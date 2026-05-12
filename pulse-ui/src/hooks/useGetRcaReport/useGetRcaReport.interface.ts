import type { ErrorAttributionResponse } from "../useGetErrorAttribution/useGetErrorAttribution.interface";

/** RCA structured report v1 (snake_case fields per pulse_ai / API JSON). */
export type RcaStructuredMetricRowV1 = {
  metric_id: string;
  metric_label: string;
  value_display: string;
  baseline_display: string;
  delta_display: string;
  value_number: number | null;
  baseline_number: number | null;
};

/** Backend-injected (`RcaRelatedHeatmapsMerger`); snake_case from API JSON. */
export type RcaHeatmapFiltersWireV1 = {
  breakpoint?: string | null;
  platform?: string | null;
  app_version?: string | null;
  geographical_region?: string | null;
  from_date?: string | null;
  to_date?: string | null;
};

export type RcaRelatedHeatmapsV1 = {
  screens?: string[] | null;
  heatmap_filters?: RcaHeatmapFiltersWireV1 | null;
};

/** NLP layer on pre-computed error-attribution drill (snake_case in API JSON). */
export type ErrorAttributionInsightV1 = {
  signal: "anr" | "non_fatal" | "api";
  summary: string;
  caveat?: string | null;
};

export type DegradingInteractionV1 = {
  interactionName: string;
  interactionCount: number;
  avgApdex: number;
  degradationWeight: number;
};

export type RcaStructuredSegmentV1 = {
  rank: number;
  title: string;
  metrics: RcaStructuredMetricRowV1[];
  /** Short user-impact line shown in segment callout when present. */
  impact?: string | null;
  insights?: string | null;
  affected_sessions?: string[] | null;
  related_heatmaps?: RcaRelatedHeatmapsV1 | null;
  degrading_interactions?: DegradingInteractionV1[] | null;
};

export type RcaStructuredReportV1 = {
  version: 1;
  executive_summary: string;
  segments: RcaStructuredSegmentV1[];
  recommendations: string[];
  /** Model-generated interpretation of pre-AI ErrorAttributionPayload when present. */
  error_attribution_insights?: ErrorAttributionInsightV1[] | null;
  /** Model-copied drill payload when insights are present (camelCase keys); preferred over legacy. */
  error_attribution?: ErrorAttributionResponse | null;
  /** Legacy cached reports only: server-merged drill before LLM carried `error_attribution`. */
  errorAttribution?: ErrorAttributionResponse | null;
};

export type SessionRcaRootCausePayload = {
  baseline: Record<string, unknown> | null;
  segments: unknown[] | null;
  mode?: string | null;
  cachedAt?: string | null;
  everythingGood?: boolean | null;
  noDataAvailable?: boolean | null;
  message?: string | null;
};

export type RcaReportPayload = {
  structured?: RcaStructuredReportV1 | null;
  /** Backend may return double-wrapped report: { report: { structured } } */
  report?: RcaReportPayload | null;
  /** Session RCA tabular data merged by backend (rcaType=SESSION only). */
  rootCausePayload?: SessionRcaRootCausePayload | null;
};

/**
 * Extracts the structured report from potentially double-wrapped payload.
 * Handles both { structured } and { report: { structured } } formats.
 */
export const extractStructuredReport = (
  payload: RcaReportPayload | null | undefined,
): RcaStructuredReportV1 | null | undefined => {
  if (payload == null) {
    return null;
  }
  // Direct structured content
  if (payload.structured != null) {
    return payload.structured;
  }
  // Nested report: { report: { structured } }
  if (payload.report?.structured != null) {
    return payload.report.structured;
  }
  return null;
};

export const isRcaStructuredReportV1WithContent = (
  structured: RcaStructuredReportV1 | null | undefined,
): boolean => {
  if (structured == null) {
    return false;
  }
  const hasAttributionInsightText = (
    structured.error_attribution_insights ?? []
  ).some(
    (row) =>
      (row.summary?.trim() ?? "") !== "" || (row.caveat?.trim() ?? "") !== "",
  );
  const drill = structured.error_attribution ?? structured.errorAttribution;
  const hasDrillPayload =
    drill != null &&
    ((drill.relatedAttributions?.length ?? 0) > 0 ||
      (drill.disclaimer?.trim() ?? "") !== "");
  const hasContent =
    (structured.executive_summary?.trim() ?? "") !== "" ||
    (structured.segments?.length ?? 0) > 0 ||
    (structured.recommendations?.length ?? 0) > 0 ||
    hasAttributionInsightText ||
    hasDrillPayload;
  return hasContent;
};

export type RcaReportResponse = {
  report?: RcaReportPayload | null;
  cached?: boolean;
  /** ISO-8601 instant when served from MySQL cache (pulse-server only) */
  cachedAt?: string | null;
  regeneratedBy?: string | null;
  regeneratedAt?: string | null;
};

/** Job lifecycle for async RCA generation (polling). */
export type RcaJobStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

/** Normalized status including client-side guard for unexpected API values. */
export type RcaNormalizedJobStatus = RcaJobStatus | "UNKNOWN";

/**
 * Full job payload from GET /v1/ai-rca/job/{jobId} (and superset for POST 202 body).
 * Some fields are only present in certain states (e.g. report on COMPLETED).
 */
export interface RcaJobResponse {
  jobId: string;
  status: RcaJobStatus;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  report?: RcaReportPayload;
  errorMessage?: string;
  isJoiningExistingJob?: boolean;
  cached?: boolean;
  cachedAt?: string;
}

export type UseGetRcaReportParams = {
  entityKey: string | null;
  date?: string | null;
  /** Must match POST body and peek GET (e.g. INTERACTION). Defaults to INTERACTION at callsite. */
  rcaType?: string;
  enabled?: boolean;
  /** Included in query key so requests refetch when project context changes (e.g. synced from URL) */
  projectId?: string | null;
  /**
   * Increment when forcing a new POST (e.g. after regenerate returns 200) while entityKey/date/project are unchanged.
   */
  requestSession?: number;
  /** RCA type to POST (default: "INTERACTION"). Use "SESSION" for session quality RCA. */
  rcaType?: string;
};
