import type { RcaReportPayload } from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";

export interface RcaReportViewProps {
  report: RcaReportPayload;
  cachedAt?: string | null;
  onRegenerate?: () => void;
  projectId?: string | null;
}
