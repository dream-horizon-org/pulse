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

export type RcaStructuredSegmentV1 = {
  rank: number;
  title: string;
  metrics: RcaStructuredMetricRowV1[];
  impact?: string | null;
  insights?: string | null;
  affected_sessions?: string[] | null;
  related_heatmaps?: RcaRelatedHeatmapsV1 | null;
};

export type RcaStructuredReportV1 = {
  version: 1;
  executive_summary: string;
  segments: RcaStructuredSegmentV1[];
  recommendations: string[];
  /** Merged on server after AI success (`RcaReportErrorAttributionMerger`); camelCase JSON. */
  errorAttribution?: ErrorAttributionResponse | null;
};

export type RcaReportPayload = {
  structured?: RcaStructuredReportV1 | null;
};

export const isRcaStructuredReportV1WithContent = (
  structured: RcaStructuredReportV1 | null | undefined,
): boolean =>
  structured?.version === 1 &&
  ((structured.executive_summary?.trim() ?? "") !== "" ||
    (structured.segments?.length ?? 0) > 0 ||
    (structured.recommendations?.length ?? 0) > 0 ||
    structured.errorAttribution != null);

export type RcaReportResponse = {
  report?: RcaReportPayload | null;
  cached?: boolean;
  /** ISO-8601 instant when served from MySQL cache (pulse-server only) */
  cachedAt?: string | null;
};

export type UseGetRcaReportParams = {
  interactionName: string | null;
  date?: string | null;
  enabled?: boolean;
  /** Included in query key so requests refetch when project context changes (e.g. synced from URL) */
  projectId?: string | null;
};
