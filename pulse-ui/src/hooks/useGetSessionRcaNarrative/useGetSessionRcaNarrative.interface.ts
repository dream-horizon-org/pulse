import type { SessionRcaData } from "../useGetSessionRca";

export type SessionRcaSegmentInsight = {
  label: string;
  impact: "critical" | "normal" | string;
  z_score: number | null;
  quality_score: number | null;
  volume_pct: number | null;
  key_finding: string;
};

export type SessionRcaNarrativeV1 = {
  version: 1;
  executive_summary: string;
  segment_insights: SessionRcaSegmentInsight[];
  recommendations: string[];
};

export type SessionRcaReportApiResponse = {
  report?: {
    narrative?: SessionRcaNarrativeV1 | null;
  } | null;
  cached?: boolean;
  cachedAt?: string | null;
};

export interface UseGetSessionRcaNarrativeParams {
  anchorDate: string | null | undefined;
  asOfIso: string | null | undefined;
  projectId: string | null | undefined;
  rootCauseData: SessionRcaData | null | undefined;
  enabled?: boolean;
}
