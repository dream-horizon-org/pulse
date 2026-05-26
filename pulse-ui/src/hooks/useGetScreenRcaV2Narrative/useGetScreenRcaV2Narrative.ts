import { useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  GET_RCA_JOB_ROUTE,
  GET_SCREEN_V2_RCA_STATUS_ROUTE,
  POST_RCA_REPORT_ROUTE,
} from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import {
  RCA_JOB_POLL_MS,
  RCA_STALE_CACHE_POLL_MS,
  RCA_TYPE,
} from "../../screens/CriticalInteractionDetails/components/RootCause/RootCause.constants";
import type {
  ScreenRcaV2JobResponse,
  ScreenRcaV2NarrativePhase,
  ScreenRcaV2Structured,
  ScreenRcaV2ReportApiResponse,
  UseGetScreenRcaV2NarrativeParams,
} from "./useGetScreenRcaV2Narrative.interface";

function extractStructured(response: ScreenRcaV2JobResponse | null | undefined): ScreenRcaV2Structured | null {
  if (!response?.report) return null;
  
  const report = response.report;
  
  // If structured is explicitly provided
  if ('structured' in report && report.structured) {
    return report.structured;
  }
  
  // If report itself is the structured data (has version and executive_summary)
  if ('version' in report && 'executive_summary' in report && 'recommendations' in report) {
    return report as ScreenRcaV2Structured;
  }
  
  return null;
}

export function useGetScreenRcaV2Narrative({
  screenName,
  windowEndIso,
  windowStartIso,
  projectId,
  enabled = true,
}: UseGetScreenRcaV2NarrativeParams) {
  const trimmedName = screenName != null ? String(screenName).trim() : "";
  const trimmedProject = projectId != null ? String(projectId).trim() : "";
  const we = windowEndIso != null ? String(windowEndIso).trim() : "";
  const ws = windowStartIso != null ? String(windowStartIso).trim() : "";

  // Tracks current in-flight job ID; null = use POST path
  const jobIdRef = useRef<string | null>(null);
  // Replication-lag guard: only retry once per job
  const completedRetryRef = useRef(false);
  // Track cachedAt of the displayed report for stale detection
  const reportCachedAtRef = useRef<string | null>(null);

  const isEnabled = enabled && trimmedName !== "" && trimmedProject !== "" && we !== "" && ws !== "";

  const query = useQuery({
    queryKey: [POST_RCA_REPORT_ROUTE.key, RCA_TYPE.SCREEN_V2, trimmedName, trimmedProject, we, ws],
    queryFn: async (): Promise<ScreenRcaV2NarrativePhase> => {
      const apiBaseUrl = getApiBaseUrl();
      const headers: Record<string, string> = { "Content-Type": "application/json" };
      if (trimmedProject !== "") headers["X-Project-ID"] = trimmedProject;

      // --- Poll path ---
      if (jobIdRef.current !== null) {
        const jobUrl = `${apiBaseUrl}${GET_RCA_JOB_ROUTE.apiPath(jobIdRef.current)}`;
        const result = await makeRequest<ScreenRcaV2JobResponse>({
          url: jobUrl,
          init: { method: GET_RCA_JOB_ROUTE.method, headers },
          unwrapped: true,
        });
        const status = result.data?.status ?? "UNKNOWN";

        if (status === "COMPLETED") {
          const structured = extractStructured(result.data);
          // Replication-lag guard: if COMPLETED but no report, retry POST once
          if (structured === null && !completedRetryRef.current) {
            completedRetryRef.current = true;
            jobIdRef.current = null;
            return { phase: "pending" };
          }
          jobIdRef.current = null;
          completedRetryRef.current = false;
          reportCachedAtRef.current = (result.data?.report as ScreenRcaV2ReportApiResponse)?.cachedAt ?? null;
          return { phase: "done", structured };
        }

        if (status === "FAILED" || status === "UNKNOWN") {
          jobIdRef.current = null;
          completedRetryRef.current = false;
          return { phase: "error", message: result.data?.errorMessage ?? "Report generation failed" };
        }

        // PENDING / PROCESSING — keep polling
        return { phase: "pending", jobId: result.data?.jobId };
      }

      // --- Initial POST path ---
      completedRetryRef.current = false;
      const url = `${apiBaseUrl}${POST_RCA_REPORT_ROUTE.apiPath}`;
      const body = {
        rcaType: RCA_TYPE.SCREEN_V2,
        entityKey: trimmedName,
        date: we,
        start: ws,
        end: we,
      };
      const result = await makeRequest<ScreenRcaV2JobResponse>({
        url,
        init: { method: POST_RCA_REPORT_ROUTE.method, body: JSON.stringify(body), headers },
        unwrapped: true,
      });

      if (result.status === 200) {
        const structured = extractStructured(result.data);
        reportCachedAtRef.current = (result.data?.report as ScreenRcaV2ReportApiResponse)?.cachedAt ?? null;
        return { phase: "done", structured };
      }
      if (result.status === 202) {
        const jobId = result.data?.jobId ?? null;
        if (jobId) jobIdRef.current = jobId;
        return { phase: "pending", jobId };
      }
      return { phase: "error", message: result.error?.message ?? "Failed to start report generation" };
    },
    enabled: isEnabled,
    refetchInterval: (q) => (q.state.data?.phase === "pending" ? RCA_JOB_POLL_MS : false),
    retry: false,
  });

  const phase = query.data?.phase;
  const hasReport = phase === "done";

  // --- Stale cache background poll ---
  // Fires after a report is displayed; detects if someone else regenerated
  const staleQuery = useQuery({
    queryKey: [GET_SCREEN_V2_RCA_STATUS_ROUTE.key, trimmedName, trimmedProject, we],
    queryFn: async (): Promise<{ stale: boolean; asyncInFlight: boolean }> => {
      const apiBaseUrl = getApiBaseUrl();
      const headers: Record<string, string> = {};
      if (trimmedProject !== "") headers["X-Project-ID"] = trimmedProject;
      const url = `${apiBaseUrl}${GET_SCREEN_V2_RCA_STATUS_ROUTE.apiPath(trimmedName, we)}`;
      const result = await makeRequest<ScreenRcaV2JobResponse>({
        url,
        init: { method: GET_SCREEN_V2_RCA_STATUS_ROUTE.method, headers },
        unwrapped: true,
      });
      const status = result.data?.status ?? null;
      const asyncInFlight = status === "PENDING" || status === "PROCESSING";
      const newCachedAt = (result.data?.report as ScreenRcaV2ReportApiResponse)?.cachedAt ?? null;
      const stale =
        status === "COMPLETED" &&
        newCachedAt !== null &&
        reportCachedAtRef.current !== null &&
        newCachedAt !== reportCachedAtRef.current;
      return { stale, asyncInFlight };
    },
    enabled: isEnabled && hasReport,
    refetchInterval: RCA_STALE_CACHE_POLL_MS,
    retry: false,
  });

  const staleDetected =
    (staleQuery.data?.stale === true) || (staleQuery.data?.asyncInFlight === true);

  return {
    structured: hasReport && query.data ? (query.data as ScreenRcaV2NarrativePhase & { structured?: ScreenRcaV2Structured | null }).structured : null,
    isLoading: query.isLoading || phase === "pending",
    isPending: phase === "pending",
    isError: query.isError || phase === "error",
    error: query.isError ? query.error : null,
    errorMessage: phase === "error" ? (query.data as ScreenRcaV2NarrativePhase & { message?: string }).message : null,
    staleDetected,
  };
}
