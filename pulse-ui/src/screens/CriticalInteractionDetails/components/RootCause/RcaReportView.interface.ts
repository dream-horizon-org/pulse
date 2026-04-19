import type { RcaReportPayload } from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";

export interface RcaReportViewProps {
  report: RcaReportPayload;
  cachedAt?: string | null;
  /** e.g. "2 minutes ago" — when set, shown as "Generated …" instead of absolute "Report as of …" */
  relativeGeneratedAt?: string | null;
  onRegenerate?: () => void;
  projectId?: string | null;
}
