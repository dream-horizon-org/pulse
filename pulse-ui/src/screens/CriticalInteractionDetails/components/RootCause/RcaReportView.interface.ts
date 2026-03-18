import type { RcaReportPayload } from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";

export interface RcaReportViewProps {
  report: RcaReportPayload;
  rcaInsights?: string | null;
  cached?: boolean;
  cachedAt?: string | null;
}
