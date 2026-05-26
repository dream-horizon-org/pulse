
import type {
  ScreenRcaProblemV2,
  ScreenRcaEvidencesV2,
} from "../useGetScreenRootCauseV2/useGetScreenRootCauseV2.interface";

// Re-export them
export type {
  ScreenRcaMetricsV2,
  ScreenRcaSpecificIssueV2,
  ScreenRcaProblemV2,
  ScreenRcaEvidencesV2,
} from "../useGetScreenRootCauseV2/useGetScreenRootCauseV2.interface";

export interface ScreenRcaV2Structured {
  version: 2;
  executive_summary: string;
  recommendations: string[];
  problems?: ScreenRcaProblemV2[] | null;
  evidences?: ScreenRcaEvidencesV2 | null;
}

export type ScreenRcaV2ReportApiResponse = {
  version?: number;
  executive_summary?: string;
  problems?: any[];
  recommendations?: string[];
  evidence?: Record<string, any>;
  cached?: boolean;
  cachedAt?: string | null;
  structured?: ScreenRcaV2Structured | null;
};

export type ScreenRcaV2JobStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | "UNKNOWN";

export type ScreenRcaV2JobResponse = {
  jobId?: string | null;
  status?: ScreenRcaV2JobStatus | null;
  report?: ScreenRcaV2ReportApiResponse | null;
  errorMessage?: string | null;
  isJoiningExistingJob?: boolean;
};

/** Internal query result phases — not exposed to consumers. */
export type ScreenRcaV2NarrativePhase =
  | { phase: "pending"; jobId?: string | null }
  | { phase: "done"; structured: ScreenRcaV2Structured | null }
  | { phase: "error"; message: string };

export interface UseGetScreenRcaV2NarrativeParams {
  screenName: string | null | undefined;
  windowEndIso: string | null | undefined;
  windowStartIso: string | null | undefined;
  projectId: string | null | undefined;
  enabled?: boolean;
}
