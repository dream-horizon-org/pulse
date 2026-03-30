export type RcaReportChartBlock = {
  type: "chart";
  title: string;
  data: Record<string, unknown>;
  description?: string | null;
};

export type RcaReportTableBlock = {
  type: "table";
  title: string;
  columns: Array<Record<string, unknown>>;
  rows: Array<Record<string, unknown>>;
  description?: string | null;
};

export type RcaReportPayload = {
  markdown?: string | null;
  charts: RcaReportChartBlock[];
  tables: RcaReportTableBlock[];
};

/** Drives mock heatmap readability when opening Screen → Heatmap from RCA evidence (GET `rcaHeatmapSignal`). */
export type RcaHeatmapSignalQuality = "poor" | "average" | "good";

export type RcaReportResponse = {
  report: RcaReportPayload;
  rca_insights?: string | null;
  cached?: boolean;
  /** Present on mock (and optional on real API): aligns heatmap fixture with RCA narrative. */
  heatmap_signal_quality?: RcaHeatmapSignalQuality;
};

/** Optional tenant-level metrics from the interaction overview (mock RCA aligns baselines). */
export type RcaReportTenantContext = {
  errorRatePercent: number;
  poorUsersPercent: number;
  apdex?: number;
  p50Ms?: number;
  p95Ms?: number;
};

export type UseGetRcaReportParams = {
  interactionName: string | null;
  date?: string | null;
  enabled?: boolean;
  projectId?: string | null;
  /** When set (e.g. from interaction details graphs), mock RCA uses these as baseline. */
  tenantContext?: RcaReportTenantContext | null;
};
