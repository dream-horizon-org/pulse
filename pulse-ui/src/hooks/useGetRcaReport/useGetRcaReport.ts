import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  GET_RCA_JOB_ROUTE,
  GET_RCA_STATUS_ROUTE,
  POST_RCA_REPORT_ROUTE,
} from "../../constants/API";
import {
  RCA_JOB_POLL_MS,
  RCA_STALE_CACHE_POLL_MS,
  RCA_TYPE,
} from "../../screens/CriticalInteractionDetails/components/RootCause/RootCause.constants";
import { makeRequest } from "../../helpers/makeRequest";
import type { ApiResponse } from "../../helpers/makeRequest";
import { isValidRcaDateParam } from "../../helpers/rcaRequestUtils";
import {
  getJobIdFromRcaPostResponse,
  unwrapRcaJobApiBody,
  unwrapRcaReportPostApiBody,
} from "../../helpers/rcaResponseUnwrap";
import { getApiBaseUrl } from "../../utils";
import {
  extractStructuredReport,
  isRcaStructuredReportV1WithContent,
  type RcaJobResponse,
  type RcaJobStatus,
  type RcaNormalizedJobStatus,
  type RcaReportResponse,
  type UseGetRcaReportParams,
} from "./useGetRcaReport.interface";
import {
  getMockInteractionRcaReportApiResponse,
  getMockInteractionRcaStaleStatusApiResponse,
} from "../../mocks/mockInteractionRcaReport";

const RCA_HTTP_ACCEPTED = 202;
const RCA_HTTP_OK = 200;

const UNEXPECTED_RCA_POST_ERROR = new Error(
  "Unexpected RCA response from server",
);

const KNOWN_RCA_JOB_STATUSES = new Set<string>([
  "PENDING",
  "PROCESSING",
  "COMPLETED",
  "FAILED",
] satisfies RcaJobStatus[]);

export function normalizeRcaJobStatus(status: unknown): RcaNormalizedJobStatus {
  if (typeof status !== "string") {
    return "UNKNOWN";
  }
  const upper = status.trim().toUpperCase();
  if (KNOWN_RCA_JOB_STATUSES.has(upper)) {
    return upper as RcaJobStatus;
  }
  return "UNKNOWN";
}

function buildProjectHeaders(projectId: string): Record<string, string> {
  const headers: Record<string, string> = {};
  const trimmed = String(projectId).trim();
  if (trimmed !== "") {
    headers["X-Project-ID"] = trimmed;
  }
  return headers;
}

function buildPostBody(
  entityKey: string,
  date: string | null | undefined,
): { rcaType: string; entityKey: string; date?: string } {
  const body: { rcaType: string; entityKey: string; date?: string } = {
    rcaType: RCA_TYPE.INTERACTION,
    entityKey,
  };
  if (isValidRcaDateParam(date)) {
    body.date = date;
  }
  return body;
}

async function requestRcaReportPost(
  entityKey: string,
  date: string | null | undefined,
  projectId: string,
): Promise<ApiResponse<RcaReportResponse | RcaJobResponse>> {
  const apiBaseUrl = getApiBaseUrl();
  const url = `${apiBaseUrl}${POST_RCA_REPORT_ROUTE.apiPath}`;
  const headers = buildProjectHeaders(projectId);
  const raw = await makeRequest<RcaReportResponse | RcaJobResponse>({
    url,
    init: {
      method: POST_RCA_REPORT_ROUTE.method,
      body: JSON.stringify(buildPostBody(entityKey, date)),
      headers,
    },
    unwrapped: true,
  });
  return unwrapRcaReportPostApiBody(raw);
}

async function requestRcaJobGet(
  jobId: string,
  projectId: string,
): Promise<ApiResponse<RcaJobResponse>> {
  const apiBaseUrl = getApiBaseUrl();
  const url = `${apiBaseUrl}${GET_RCA_JOB_ROUTE.apiPath(jobId)}`;
  const headers = buildProjectHeaders(projectId);
  const raw = await makeRequest<RcaJobResponse>({
    url,
    init: {
      method: GET_RCA_JOB_ROUTE.method,
      headers,
    },
    unwrapped: true,
  });
  return unwrapRcaJobApiBody(raw);
}

async function requestRcaStatusGet(
  entityKey: string,
  date: string | null | undefined,
  projectId: string,
): Promise<ApiResponse<RcaJobResponse>> {
  const apiBaseUrl = getApiBaseUrl();
  const url = `${apiBaseUrl}${GET_RCA_STATUS_ROUTE.apiPath(entityKey, date)}`;
  const headers = buildProjectHeaders(projectId);
  return makeRequest<RcaJobResponse>({
    url,
    init: {
      method: GET_RCA_STATUS_ROUTE.method,
      headers,
    },
  });
}

function toReportApiResponse(
  job: RcaJobResponse,
): ApiResponse<RcaReportResponse> | null {
  if (job.status !== "COMPLETED" || job.report == null) {
    return null;
  }
  return {
    status: RCA_HTTP_OK,
    data: {
      report: job.report,
      cached: job.cached ?? true,
      cachedAt: job.cachedAt ?? job.completedAt ?? null,
    },
    error: null,
  };
}

function normalizeJobPayload(
  raw: RcaJobResponse | RcaReportResponse | null | undefined,
  fallbackJobId: string | null,
): RcaJobResponse | null {
  if (raw == null || typeof raw !== "object") {
    return null;
  }
  let candidate: RcaJobResponse | RcaReportResponse | null | undefined = raw;
  if (!("jobId" in candidate)) {
    const nested = (candidate as { data?: unknown }).data;
    if (
      nested != null &&
      typeof nested === "object" &&
      "jobId" in nested &&
      "status" in nested
    ) {
      candidate = nested as RcaJobResponse;
    }
  }
  if (
    candidate == null ||
    typeof candidate !== "object" ||
    !("jobId" in candidate) ||
    !("status" in candidate)
  ) {
    return null;
  }
  const j = candidate as RcaJobResponse;
  const jobId = typeof j.jobId === "string" ? j.jobId : (fallbackJobId ?? "");
  if (!jobId) {
    return null;
  }
  return {
    ...j,
    jobId,
  };
}

/**
 * Fetches the AI-generated RCA report for an entity.
 * POST /v1/ai/rca/report; on 202 polls GET /v1/ai-rca/job/{jobId} until
 * COMPLETED or FAILED. On 200 returns cached report immediately.
 *
 * - **`retry`**: Clears the active job poll (if any), resets local job id, and invalidates the
 *   initial POST query so a new RCA flow starts (use after FAILED or to force a fresh POST).
 */
export function useGetRcaReport({
  entityKey,
  date,
  enabled = true,
  projectId,
  requestSession = 0,
}: UseGetRcaReportParams) {
  const queryClient = useQueryClient();
  const trimmedProjectId =
    projectId != null && String(projectId).trim() !== ""
      ? String(projectId).trim()
      : "";
  const baseEnabled = enabled && !!entityKey && trimmedProjectId !== "";

  const [pollJobId, setPollJobId] = useState<string | null>(null);
  const autoRetryCompletedMissRef = useRef(false);

  useEffect(() => {
    setPollJobId(null);
    autoRetryCompletedMissRef.current = false;
  }, [entityKey, date, trimmedProjectId, requestSession]);

  const postReportQuery = useQuery({
    queryKey: [
      POST_RCA_REPORT_ROUTE.key,
      entityKey,
      date ?? null,
      trimmedProjectId,
      "post",
      requestSession,
    ],
    queryFn: async (): Promise<
      ApiResponse<RcaReportResponse | RcaJobResponse>
    > => {
      if (!entityKey) {
        return {
          data: null,
          error: {
            code: "400",
            message: "Entity key required",
            cause: "",
          },
          status: 400,
        };
      }
      if (process.env.REACT_APP_USE_MOCK_SERVER === "true") {
        return getMockInteractionRcaReportApiResponse(entityKey);
      }
      return requestRcaReportPost(entityKey, date ?? null, trimmedProjectId);
    },
    enabled: baseEnabled && pollJobId === null,
    retry: false,
    staleTime: Number.POSITIVE_INFINITY,
  });

  useEffect(() => {
    if (pollJobId !== null) {
      return;
    }
    const res = postReportQuery.data;
    if (!res || postReportQuery.isPending) {
      return;
    }
    if (res.status === RCA_HTTP_ACCEPTED) {
      const id = getJobIdFromRcaPostResponse(res);
      if (id) {
        setPollJobId(id);
      }
    }
  }, [pollJobId, postReportQuery.data, postReportQuery.isPending]);

  const jobStatusQuery = useQuery({
    queryKey: [
      POST_RCA_REPORT_ROUTE.key,
      trimmedProjectId,
      GET_RCA_JOB_ROUTE.key,
      pollJobId,
    ],
    queryFn: async (): Promise<ApiResponse<RcaJobResponse>> => {
      if (!pollJobId) {
        return {
          data: null,
          error: {
            code: "400",
            message: "Missing job id",
            cause: "",
          },
          status: 400,
        };
      }
      return requestRcaJobGet(pollJobId, trimmedProjectId);
    },
    enabled: baseEnabled && pollJobId !== null,
    refetchInterval: (query) => {
      const payload = query.state.data?.data;
      const st = normalizeRcaJobStatus(
        payload != null && typeof payload === "object" && "status" in payload
          ? (payload as { status?: unknown }).status
          : undefined,
      );
      if (st === "COMPLETED" || st === "FAILED" || st === "UNKNOWN") {
        return false;
      }
      return RCA_JOB_POLL_MS;
    },
    retry: false,
  });

  const jobSnapshot = useMemo((): RcaJobResponse | null => {
    const jobPoll = normalizeJobPayload(jobStatusQuery.data?.data, pollJobId);
    if (jobPoll) {
      return jobPoll;
    }
    if (postReportQuery.data?.status === RCA_HTTP_ACCEPTED) {
      return normalizeJobPayload(
        postReportQuery.data.data as RcaJobResponse,
        pollJobId,
      );
    }
    return null;
  }, [jobStatusQuery.data, postReportQuery.data, pollJobId]);

  // When polling returns COMPLETED but no report body (backend cache miss / replication lag),
  // automatically retry once by restarting the POST flow — which will hit the cache directly.
  useEffect(() => {
    if (
      jobSnapshot != null &&
      jobSnapshot.status === "COMPLETED" &&
      jobSnapshot.report == null &&
      !autoRetryCompletedMissRef.current
    ) {
      autoRetryCompletedMissRef.current = true;
      void retry();
    }
    // retry is stable (useCallback), jobSnapshot identity changes on each new poll result
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobSnapshot]);

  const mergedData = useMemo(():
    | ApiResponse<RcaReportResponse>
    | ApiResponse<RcaJobResponse>
    | undefined => {
    if (postReportQuery.data?.status === RCA_HTTP_OK) {
      return postReportQuery.data as ApiResponse<RcaReportResponse>;
    }
    // Completed job result takes priority over the stale 202 POST response so the
    // polled report is shown without an extra round-trip.  Falls through to the 202
    // branch when the job is COMPLETED but has no report yet (replication lag), which
    // keeps the loading UI visible until the auto-retry fires.
    const completed =
      jobSnapshot?.status === "COMPLETED"
        ? toReportApiResponse(jobSnapshot)
        : null;
    if (completed) {
      return completed;
    }
    if (postReportQuery.data?.status === RCA_HTTP_ACCEPTED) {
      return postReportQuery.data as ApiResponse<RcaJobResponse>;
    }
    return undefined;
  }, [postReportQuery.data, jobSnapshot]);

  const hasDisplayableCompletedReport = useMemo(
    () =>
      mergedData?.status === RCA_HTTP_OK &&
      mergedData.data != null &&
      isRcaStructuredReportV1WithContent(
        extractStructuredReport(mergedData.data.report),
      ),
    [mergedData],
  );

  const staleCachePollQuery = useQuery({
    queryKey: [
      GET_RCA_STATUS_ROUTE.key,
      entityKey,
      date ?? null,
      trimmedProjectId,
      "cache-stale-poll",
      requestSession,
    ],
    queryFn: async (): Promise<ApiResponse<RcaJobResponse>> => {
      if (!entityKey) {
        return {
          data: null,
          error: {
            code: "400",
            message: "Entity key required",
            cause: "",
          },
          status: 400,
        };
      }
      if (process.env.REACT_APP_USE_MOCK_SERVER === "true") {
        return getMockInteractionRcaStaleStatusApiResponse();
      }
      return requestRcaStatusGet(entityKey, date ?? null, trimmedProjectId);
    },
    enabled: baseEnabled && !!entityKey && hasDisplayableCompletedReport,
    refetchInterval: RCA_STALE_CACHE_POLL_MS,
    retry: false,
  });

  const staleRegenerationDetected = useMemo(() => {
    if (!hasDisplayableCompletedReport) {
      return false;
    }
    const displayed = mergedData?.data?.cachedAt;
    const polled = staleCachePollQuery.data;
    if (!polled || polled.status !== RCA_HTTP_OK || !polled.data) {
      return false;
    }
    const job = polled.data as RcaJobResponse;
    if (normalizeRcaJobStatus(job.status) !== "COMPLETED") {
      return false;
    }
    const polledCachedAt = job.cachedAt;
    if (
      displayed == null ||
      polledCachedAt == null ||
      String(polledCachedAt).trim() === ""
    ) {
      return false;
    }
    return String(polledCachedAt) !== String(displayed);
  }, [
    hasDisplayableCompletedReport,
    mergedData?.data?.cachedAt,
    staleCachePollQuery.data,
  ]);

  const stalePollAsyncJobDetected = useMemo(() => {
    if (!hasDisplayableCompletedReport) {
      return false;
    }
    const polled = staleCachePollQuery.data;
    if (!polled || polled.status !== RCA_HTTP_OK || !polled.data) {
      return false;
    }
    const job = polled.data as RcaJobResponse;
    const statusStr = normalizeRcaJobStatus(job.status);
    return (
      (statusStr === "PENDING" || statusStr === "PROCESSING") && !!job.jobId
    );
  }, [hasDisplayableCompletedReport, staleCachePollQuery.data]);

  const isAsyncBootstrapping = pollJobId === null && postReportQuery.isLoading;

  const isAwaitingPollPayload = pollJobId !== null && jobStatusQuery.isLoading;

  const isLoading =
    pollJobId === null ? postReportQuery.isLoading : jobStatusQuery.isLoading;

  const isFetching =
    pollJobId === null ? postReportQuery.isFetching : jobStatusQuery.isFetching;

  const postUnexpectedStatus =
    postReportQuery.isSuccess &&
    postReportQuery.data != null &&
    postReportQuery.data.status !== RCA_HTTP_OK &&
    postReportQuery.data.status !== RCA_HTTP_ACCEPTED;

  const isError =
    pollJobId === null
      ? postReportQuery.isError || postUnexpectedStatus
      : jobStatusQuery.isError;

  const errorDetail =
    pollJobId === null
      ? postUnexpectedStatus
        ? UNEXPECTED_RCA_POST_ERROR
        : postReportQuery.error
      : jobStatusQuery.error;

  const retry = useCallback(async () => {
    if (pollJobId) {
      queryClient.removeQueries({
        queryKey: [
          POST_RCA_REPORT_ROUTE.key,
          trimmedProjectId,
          GET_RCA_JOB_ROUTE.key,
          pollJobId,
        ],
      });
    }
    setPollJobId(null);
    await queryClient.invalidateQueries({
      queryKey: [
        POST_RCA_REPORT_ROUTE.key,
        entityKey,
        date ?? null,
        trimmedProjectId,
        "post",
        requestSession,
      ],
      refetchType: "all",
    });
  }, [
    pollJobId,
    queryClient,
    trimmedProjectId,
    entityKey,
    date,
    requestSession,
  ]);

  const beginFollowingJob = useCallback(
    (jobId: string) => {
      const id = String(jobId).trim();
      if (!id) {
        return;
      }
      queryClient.removeQueries({
        queryKey: [
          POST_RCA_REPORT_ROUTE.key,
          entityKey,
          date ?? null,
          trimmedProjectId,
          "post",
          requestSession,
        ],
      });
      setPollJobId(id);
    },
    [queryClient, entityKey, date, trimmedProjectId, requestSession],
  );

  const normalizedJobStatus =
    jobSnapshot != null ? normalizeRcaJobStatus(jobSnapshot.status) : null;
  const isRcaQueuePending = normalizedJobStatus === "PENDING";
  const isProcessing = normalizedJobStatus === "PROCESSING";
  const isCompleted = normalizedJobStatus === "COMPLETED";
  const isFailed = normalizedJobStatus === "FAILED";
  const isUnknown = normalizedJobStatus === "UNKNOWN";

  const isRetrying =
    postReportQuery.isFetching &&
    pollJobId === null &&
    (postReportQuery.data?.status === RCA_HTTP_ACCEPTED ||
      jobSnapshot?.status === "FAILED");

  return {
    data: mergedData,
    isLoading,
    isFetching,
    isError,
    error: errorDetail,
    isRcaQueuePending,
    isProcessing,
    isCompleted,
    isFailed,
    isUnknown,
    jobId: pollJobId,
    errorMessage: jobSnapshot?.errorMessage ?? null,
    isJoiningExistingJob: jobSnapshot?.isJoiningExistingJob ?? false,
    retry,
    isRetrying,
    beginFollowingJob,
    staleRegenerationDetected,
    stalePollAsyncJobDetected,
    isAsyncBootstrapping,
    isAwaitingPollPayload,
  };
}
