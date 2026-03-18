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

export type RcaReportResponse = {
  report: RcaReportPayload;
  rca_insights?: string | null;
  cached?: boolean;
};

export type UseGetRcaReportParams = {
  interactionName: string | null;
  date?: string | null;
  enabled?: boolean;
  /** Included in query key so requests refetch when project context changes (e.g. synced from URL) */
  projectId?: string | null;
};
