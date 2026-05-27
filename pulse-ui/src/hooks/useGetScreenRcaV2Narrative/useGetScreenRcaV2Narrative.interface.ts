
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
  ScreenRcaIssueSessionEvidenceV2,
} from "../useGetScreenRootCauseV2/useGetScreenRootCauseV2.interface";

export interface ScreenRcaV2Structured {
  version: 2;
  executive_summary: string;
  recommendations: string[];
  problems?: ScreenRcaProblemV2[] | null;
  evidences?: ScreenRcaEvidencesV2 | null;
}

export type ScreenRcaV2ReportPayload = {
  structured?: ScreenRcaV2Structured | null;
  report?: ScreenRcaV2ReportPayload | null;
};

export type ScreenRcaV2ReportApiResponse = {
  version?: number;
  executive_summary?: string;
  problems?: ScreenRcaProblemV2[] | null;
  recommendations?: string[];
  evidence?: Record<string, unknown>;
  cached?: boolean;
  cachedAt?: string | null;
  structured?: ScreenRcaV2Structured | null;
  report?: ScreenRcaV2ReportPayload | null;
};

export type ScreenRcaV2JobStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

export type ScreenRcaV2JobResponse = {
  jobId?: string | null;
  status?: ScreenRcaV2JobStatus | string | null;
  report?: ScreenRcaV2ReportApiResponse | ScreenRcaV2ReportPayload | null;
  errorMessage?: string | null;
  isJoiningExistingJob?: boolean;
  cached?: boolean;
  cachedAt?: string | null;
  completedAt?: string | null;
};

export function extractScreenRcaV2Structured(
  payload: ScreenRcaV2ReportApiResponse | ScreenRcaV2ReportPayload | ScreenRcaV2JobResponse | null | undefined,
): ScreenRcaV2Structured | null {
  if (payload == null || typeof payload !== "object") {
    return null;
  }
  if ("status" in payload && "report" in payload && payload.report != null) {
    return extractScreenRcaV2Structured(payload.report as ScreenRcaV2ReportApiResponse);
  }
  const reportPayload = payload as ScreenRcaV2ReportApiResponse;
  if (reportPayload.structured != null) {
    return reportPayload.structured;
  }
  if (reportPayload.report?.structured != null) {
    return reportPayload.report.structured;
  }
  if (
    reportPayload.version === 2 &&
    "executive_summary" in reportPayload &&
    "recommendations" in reportPayload
  ) {
    return reportPayload as ScreenRcaV2Structured;
  }
  return null;
}

export function isScreenRcaV2ReportReady(
  structured: ScreenRcaV2Structured | null | undefined,
): boolean {
  return structured != null && structured.version === 2;
}

export interface UseGetScreenRcaV2NarrativeParams {
  screenName: string | null | undefined;
  windowEndIso: string | null | undefined;
  windowStartIso: string | null | undefined;
  projectId: string | null | undefined;
  enabled?: boolean;
  /**
   * Increment when forcing a new POST (e.g. after regenerate returns 200) while keys are unchanged.
   */
  requestSession?: number;
}
