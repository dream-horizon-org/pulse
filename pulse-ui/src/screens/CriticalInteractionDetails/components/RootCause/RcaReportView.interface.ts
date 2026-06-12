import type { RcaReportPayload } from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";

/** Optional funnel (or other) context shown above the report body, aligned with report padding. */
export interface RcaReportContextProps {
  badge?: string;
  title: string;
  subtitle?: string;
  hint?: string;
}

export interface RcaReportViewProps {
  report: RcaReportPayload;
  cachedAt?: string | null;
  /** e.g. "2 minutes ago" — when set, shown as "Generated …" instead of absolute "Report as of …" */
  relativeGeneratedAt?: string | null;
  onRegenerate?: () => void;
  projectId?: string | null;
  reportContext?: RcaReportContextProps;
}
