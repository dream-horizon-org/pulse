import { useCallback, useState } from "react";
import {
  GET_RCA_JOB_ROUTE,
  POST_INTERACTION_REPORT_ROUTE,
} from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import {
  getJobIdFromRcaPostResponse,
  unwrapRcaJobApiBody,
  unwrapRcaReportPostApiBody,
} from "../../helpers/rcaResponseUnwrap";
import { isValidRcaDateParam } from "../../helpers/rcaRequestUtils";
import { normalizeRcaJobStatus } from "../useGetRcaReport/useGetRcaReport";
import type {
  RcaJobResponse,
  RcaReportResponse,
} from "../useGetRcaReport/useGetRcaReport.interface";
import { getApiBaseUrl } from "../../utils";
import {
  extractCacheMeta,
  extractInteractionReport,
} from "./extractInteractionReport";
import type {
  InteractionReportV1Wire,
  UseInteractionReportParams,
  UseInteractionReportResult,
} from "./useInteractionReport.interface";

const RCA_HTTP_OK = 200;
const RCA_HTTP_ACCEPTED = 202;
const POLL_INTERVAL_MS = 2000;
const POLL_MAX_ATTEMPTS = 120;

function buildHeaders(projectId?: string): Record<string, string> {
  const headers: Record<string, string> = {};
  if (projectId?.trim()) {
    headers["X-Project-ID"] = projectId.trim();
  }
  return headers;
}

export function useInteractionReport({
  entityKey,
  date,
  projectId,
}: UseInteractionReportParams): UseInteractionReportResult {
  const [report, setReport] = useState<InteractionReportV1Wire | null>(null);
  const [cached, setCached] = useState(false);
  const [cachedAt, setCachedAt] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const applyPayload = useCallback((data: unknown) => {
    const parsed = extractInteractionReport(data);
    if (parsed) {
      setReport(parsed);
    }
    const meta = extractCacheMeta(data);
    setCached(meta.cached);
    setCachedAt(meta.cachedAt);
  }, []);

  const pollJob = useCallback(
    async (jobId: string) => {
      const headers = buildHeaders(projectId);
      for (let attempt = 0; attempt < POLL_MAX_ATTEMPTS; attempt++) {
        const jobRes = await makeRequest<RcaJobResponse>({
          url: `${getApiBaseUrl()}${GET_RCA_JOB_ROUTE.apiPath(jobId)}`,
          init: { method: GET_RCA_JOB_ROUTE.method, headers },
          unwrapped: true,
        });
        const job = unwrapRcaJobApiBody(jobRes).data;
        const status = normalizeRcaJobStatus(job?.status);
        if (status === "COMPLETED") {
          applyPayload(job?.report ?? job);
          return;
        }
        if (status === "FAILED") {
          throw new Error(job?.errorMessage ?? "Report generation failed");
        }
        await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS));
      }
      throw new Error("Report generation timed out");
    },
    [applyPayload, projectId],
  );

  const generate = useCallback(
    async (regenerate = false) => {
      if (!entityKey?.trim()) return;
      setLoading(true);
      setError(null);
      try {
        const body: Record<string, string | boolean> = {
          entityKey: entityKey.trim(),
        };
        if (isValidRcaDateParam(date)) body.date = date!;
        if (regenerate) body.regenerate = true;

        const postRes = await makeRequest<RcaReportResponse | RcaJobResponse>({
          url: `${getApiBaseUrl()}${POST_INTERACTION_REPORT_ROUTE.apiPath}`,
          init: {
            method: POST_INTERACTION_REPORT_ROUTE.method,
            headers: {
              "Content-Type": "application/json",
              ...buildHeaders(projectId),
            },
            body: JSON.stringify(body),
          },
          unwrapped: true,
        });
        const unwrapped = unwrapRcaReportPostApiBody(postRes);
        if (postRes.status === RCA_HTTP_OK) {
          applyPayload(unwrapped.data);
          return;
        }
        if (postRes.status === RCA_HTTP_ACCEPTED) {
          const jobId = getJobIdFromRcaPostResponse(postRes);
          if (!jobId) throw new Error("Missing job id from server");
          await pollJob(jobId);
          return;
        }
        throw new Error("Unexpected response from interaction report API");
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to load report");
        setReport(null);
        setCached(false);
        setCachedAt(null);
      } finally {
        setLoading(false);
      }
    },
    [applyPayload, date, entityKey, pollJob, projectId],
  );

  return {
    report,
    cached,
    cachedAt,
    loading,
    error,
    generate,
  };
}
