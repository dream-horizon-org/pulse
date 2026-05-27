import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import {
  GET_RCA_JOB_ROUTE,
  GET_SCREEN_V2_RCA_STATUS_ROUTE,
  POST_RCA_REPORT_ROUTE,
} from "../../constants/API";
import {
  RCA_JOB_POLL_MS,
  RCA_STALE_CACHE_POLL_MS,
  RCA_TYPE,
} from "../../screens/CriticalInteractionDetails/components/RootCause/RootCause.constants";
import { makeRequest } from "../../helpers/makeRequest";
import type { ApiResponse } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import { normalizeRcaJobStatus } from "../useGetRcaReport/useGetRcaReport";
import { getJobIdFromScreenV2PostResponse } from "../useRegenerateScreenRcaV2Narrative/useRegenerateScreenRcaV2Narrative";
import type {
  ScreenRcaV2JobResponse,
  ScreenRcaV2ReportApiResponse,
  ScreenRcaV2Structured,
  UseGetScreenRcaV2NarrativeParams,
} from "./useGetScreenRcaV2Narrative.interface";
import {
  extractScreenRcaV2Structured,
  isScreenRcaV2ReportReady,
} from "./useGetScreenRcaV2Narrative.interface";

function unwrapScreenV2PostApiBody(
  res: ApiResponse<ScreenRcaV2ReportApiResponse | ScreenRcaV2JobResponse>,
): ApiResponse<ScreenRcaV2ReportApiResponse | ScreenRcaV2JobResponse> {
  const { data } = res;
  if (data == null || typeof data !== "object") {
    return res;
  }
  if ("jobId" in data || "report" in data || "cachedAt" in data) {
    return res;
  }
  const inner = (data as { data?: unknown }).data;
  if (inner != null && typeof inner === "object") {
    return { ...res, data: inner as ScreenRcaV2ReportApiResponse | ScreenRcaV2JobResponse };
  }
  return res;
}

function unwrapScreenV2JobApiBody(
  res: ApiResponse<ScreenRcaV2JobResponse>,
): ApiResponse<ScreenRcaV2JobResponse> {
  const { data } = res;
  if (data == null || typeof data !== "object") {
    return res;
  }
  if ("jobId" in data && "status" in data) {
    return res;
  }
  const inner = (data as { data?: unknown }).data;
  if (inner != null && typeof inner === "object" && "jobId" in inner && "status" in inner) {
    return { ...res, data: inner as ScreenRcaV2JobResponse };
  }
  return res;
}

dayjs.extend(utc);

const RCA_HTTP_ACCEPTED = 202;
const RCA_HTTP_OK = 200;

const UNEXPECTED_RCA_POST_ERROR = new Error(
  "Unexpected RCA response from server",
);

function buildProjectHeaders(projectId: string): Record<string, string> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const trimmed = String(projectId).trim();
  if (trimmed !== "") {
    headers["X-Project-ID"] = trimmed;
  }
  return headers;
}

function reportDateFromWindowEnd(windowEndIso: string): string {
  const endMs = Date.parse(windowEndIso);
  if (Number.isNaN(endMs)) {
    return windowEndIso;
  }
  return dayjs.utc(endMs).format("YYYY-MM-DD");
}

function buildScreenV2PostBody(
  screenName: string,
  windowStartIso: string,
  windowEndIso: string,
  reportDate: string,
  regenerate?: boolean,
): Record<string, string | boolean> {
  const body: Record<string, string | boolean> = {
    rcaType: RCA_TYPE.SCREEN_V2,
    entityKey: screenName,
    date: reportDate,
    start: windowStartIso,
    end: windowEndIso,
  };
  if (regenerate) {
    body.regenerate = true;
  }
  return body;
}

async function requestScreenV2RcaReportPost(
  screenName: string,
  windowStartIso: string,
  windowEndIso: string,
  reportDate: string,
  projectId: string,
): Promise<ApiResponse<ScreenRcaV2ReportApiResponse | ScreenRcaV2JobResponse>> {
  const apiBaseUrl = getApiBaseUrl();
  const url = `${apiBaseUrl}${POST_RCA_REPORT_ROUTE.apiPath}`;
  const headers = buildProjectHeaders(projectId);
  const raw = await makeRequest<ScreenRcaV2ReportApiResponse | ScreenRcaV2JobResponse>({
    url,
    init: {
      method: POST_RCA_REPORT_ROUTE.method,
      body: JSON.stringify(
        buildScreenV2PostBody(screenName, windowStartIso, windowEndIso, reportDate),
      ),
      headers,
    },
    unwrapped: true,
  });
  return unwrapScreenV2PostApiBody(raw);
}

async function requestScreenV2RcaJobGet(
  jobId: string,
  projectId: string,
): Promise<ApiResponse<ScreenRcaV2JobResponse>> {
  const apiBaseUrl = getApiBaseUrl();
  const url = `${apiBaseUrl}${GET_RCA_JOB_ROUTE.apiPath(jobId)}`;
  const headers = buildProjectHeaders(projectId);
  const raw = await makeRequest<ScreenRcaV2JobResponse>({
    url,
    init: {
      method: GET_RCA_JOB_ROUTE.method,
      headers,
    },
    unwrapped: true,
  });
  return unwrapScreenV2JobApiBody(raw);
}

async function requestScreenV2RcaStatusGet(
  screenName: string,
  reportDate: string,
  projectId: string,
): Promise<ApiResponse<ScreenRcaV2JobResponse>> {
  const apiBaseUrl = getApiBaseUrl();
  const url = `${apiBaseUrl}${GET_SCREEN_V2_RCA_STATUS_ROUTE.apiPath(screenName, reportDate)}`;
  const headers = buildProjectHeaders(projectId);
  return makeRequest<ScreenRcaV2JobResponse>({
    url,
    init: {
      method: GET_SCREEN_V2_RCA_STATUS_ROUTE.method,
      headers,
    },
  });
}

function toScreenV2ReportApiResponse(
  job: ScreenRcaV2JobResponse,
): ApiResponse<ScreenRcaV2ReportApiResponse> | null {
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

function normalizeScreenV2JobPayload(
  raw: ScreenRcaV2JobResponse | ScreenRcaV2ReportApiResponse | null | undefined,
  fallbackJobId: string | null,
): ScreenRcaV2JobResponse | null {
  if (raw == null || typeof raw !== "object") {
    return null;
  }
  let candidate: ScreenRcaV2JobResponse | ScreenRcaV2ReportApiResponse | null | undefined = raw;
  if (!("jobId" in candidate)) {
    const nested = (candidate as { data?: unknown }).data;
    if (
      nested != null &&
      typeof nested === "object" &&
      "jobId" in nested &&
      "status" in nested
    ) {
      candidate = nested as ScreenRcaV2JobResponse;
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
  const job = candidate as ScreenRcaV2JobResponse;
  const jobId = typeof job.jobId === "string" ? job.jobId : (fallbackJobId ?? "");
  if (!jobId) {
    return null;
  }
  return {
    ...job,
    jobId,
  };
}

/**
 * Fetches the AI-generated Screen RCA v2 narrative.
 * POST /v1/ai/rca/report; on 202 polls GET /v1/ai-rca/job/{jobId} until COMPLETED or FAILED.
 * Mirrors {@link useGetRcaReport} (interaction RCA) for symmetric async job handling.
 */
export function useGetScreenRcaV2Narrative({
  screenName,
  windowEndIso,
  windowStartIso,
  projectId,
  enabled = true,
  requestSession = 0,
}: UseGetScreenRcaV2NarrativeParams) {
  const queryClient = useQueryClient();
  const trimmedName = screenName != null ? String(screenName).trim() : "";
  const trimmedProject = projectId != null ? String(projectId).trim() : "";
  const we = windowEndIso != null ? String(windowEndIso).trim() : "";
  const ws = windowStartIso != null ? String(windowStartIso).trim() : "";
  const reportDate = we !== "" ? reportDateFromWindowEnd(we) : "";

  const baseEnabled =
    enabled && trimmedName !== "" && trimmedProject !== "" && we !== "" && ws !== "";

  const [pollJobId, setPollJobId] = useState<string | null>(null);
  const autoRetryCompletedMissRef = useRef(false);

  useEffect(() => {
    setPollJobId(null);
    autoRetryCompletedMissRef.current = false;
  }, [trimmedName, trimmedProject, ws, we, reportDate, requestSession]);

  const postReportQuery = useQuery({
    queryKey: [
      POST_RCA_REPORT_ROUTE.key,
      RCA_TYPE.SCREEN_V2,
      trimmedName,
      trimmedProject,
      ws,
      we,
      reportDate,
      "post",
      requestSession,
    ],
    queryFn: async (): Promise<
      ApiResponse<ScreenRcaV2ReportApiResponse | ScreenRcaV2JobResponse>
    > => {
      if (!trimmedName) {
        return {
          data: null,
          error: {
            code: "400",
            message: "Screen name required",
            cause: "",
          },
          status: 400,
        };
      }
      return requestScreenV2RcaReportPost(trimmedName, ws, we, reportDate, trimmedProject);
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
      const id = getJobIdFromScreenV2PostResponse(res);
      if (id) {
        setPollJobId(id);
      }
    }
  }, [pollJobId, postReportQuery.data, postReportQuery.isPending]);

  const jobStatusQuery = useQuery({
    queryKey: [
      POST_RCA_REPORT_ROUTE.key,
      RCA_TYPE.SCREEN_V2,
      trimmedProject,
      GET_RCA_JOB_ROUTE.key,
      pollJobId,
    ],
    queryFn: async (): Promise<ApiResponse<ScreenRcaV2JobResponse>> => {
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
      return requestScreenV2RcaJobGet(pollJobId, trimmedProject);
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

  const jobSnapshot = useMemo((): ScreenRcaV2JobResponse | null => {
    const jobPoll = normalizeScreenV2JobPayload(jobStatusQuery.data?.data, pollJobId);
    if (jobPoll) {
      return jobPoll;
    }
    if (postReportQuery.data?.status === RCA_HTTP_ACCEPTED) {
      return normalizeScreenV2JobPayload(
        postReportQuery.data.data as ScreenRcaV2JobResponse,
        pollJobId,
      );
    }
    return null;
  }, [jobStatusQuery.data, postReportQuery.data, pollJobId]);

  const retry = useCallback(async () => {
    if (pollJobId) {
      queryClient.removeQueries({
        queryKey: [
          POST_RCA_REPORT_ROUTE.key,
          RCA_TYPE.SCREEN_V2,
          trimmedProject,
          GET_RCA_JOB_ROUTE.key,
          pollJobId,
        ],
      });
    }
    setPollJobId(null);
    await queryClient.invalidateQueries({
      queryKey: [
        POST_RCA_REPORT_ROUTE.key,
        RCA_TYPE.SCREEN_V2,
        trimmedName,
        trimmedProject,
        ws,
        we,
        reportDate,
        "post",
        requestSession,
      ],
      refetchType: "all",
    });
  }, [
    pollJobId,
    queryClient,
    trimmedProject,
    trimmedName,
    ws,
    we,
    reportDate,
    requestSession,
  ]);

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobSnapshot]);

  const mergedData = useMemo(():
    | ApiResponse<ScreenRcaV2ReportApiResponse>
    | ApiResponse<ScreenRcaV2JobResponse>
    | undefined => {
    if (postReportQuery.data?.status === RCA_HTTP_OK) {
      return postReportQuery.data as ApiResponse<ScreenRcaV2ReportApiResponse>;
    }
    const completed =
      jobSnapshot?.status === "COMPLETED"
        ? toScreenV2ReportApiResponse(jobSnapshot)
        : null;
    if (completed) {
      return completed;
    }
    if (postReportQuery.data?.status === RCA_HTTP_ACCEPTED) {
      return postReportQuery.data as ApiResponse<ScreenRcaV2JobResponse>;
    }
    return undefined;
  }, [postReportQuery.data, jobSnapshot]);

  const structured = useMemo((): ScreenRcaV2Structured | null => {
    if (mergedData?.status !== RCA_HTTP_OK || mergedData.data == null) {
      return null;
    }
    return extractScreenRcaV2Structured(mergedData.data);
  }, [mergedData]);

  const hasDisplayableCompletedReport = useMemo(
    () => mergedData?.status === RCA_HTTP_OK && isScreenRcaV2ReportReady(structured),
    [mergedData?.status, structured],
  );

  const staleCachePollQuery = useQuery({
    queryKey: [
      GET_SCREEN_V2_RCA_STATUS_ROUTE.key,
      trimmedName,
      reportDate,
      trimmedProject,
      ws,
      we,
      "cache-stale-poll",
      requestSession,
    ],
    queryFn: async (): Promise<ApiResponse<ScreenRcaV2JobResponse>> => {
      if (!trimmedName) {
        return {
          data: null,
          error: {
            code: "400",
            message: "Screen name required",
            cause: "",
          },
          status: 400,
        };
      }
      return requestScreenV2RcaStatusGet(trimmedName, reportDate, trimmedProject);
    },
    enabled: baseEnabled && hasDisplayableCompletedReport,
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
    const job = polled.data;
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
    const job = polled.data;
    const statusStr = normalizeRcaJobStatus(job.status);
    return (
      (statusStr === "PENDING" || statusStr === "PROCESSING") && !!job.jobId
    );
  }, [hasDisplayableCompletedReport, staleCachePollQuery.data]);

  const beginFollowingJob = useCallback(
    (jobId: string) => {
      const id = String(jobId).trim();
      if (!id) {
        return;
      }
      queryClient.removeQueries({
        queryKey: [
          POST_RCA_REPORT_ROUTE.key,
          RCA_TYPE.SCREEN_V2,
          trimmedName,
          trimmedProject,
          ws,
          we,
          reportDate,
          "post",
          requestSession,
        ],
      });
      setPollJobId(id);
    },
    [queryClient, trimmedName, trimmedProject, ws, we, reportDate, requestSession],
  );

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

  const isRetrying =
    postReportQuery.isFetching &&
    pollJobId === null &&
    (postReportQuery.data?.status === RCA_HTTP_ACCEPTED ||
      jobSnapshot?.status === "FAILED");

  const normalizedJobStatus =
    jobSnapshot != null ? normalizeRcaJobStatus(jobSnapshot.status) : null;

  return {
    data: mergedData,
    structured,
    isLoading,
    isFetching,
    isError,
    error: errorDetail,
    errorMessage: jobSnapshot?.errorMessage ?? null,
    isPending:
      normalizedJobStatus === "PENDING" || normalizedJobStatus === "PROCESSING",
    isRcaQueuePending: normalizedJobStatus === "PENDING",
    isProcessing: normalizedJobStatus === "PROCESSING",
    isCompleted: normalizedJobStatus === "COMPLETED",
    isFailed: normalizedJobStatus === "FAILED",
    isUnknown: normalizedJobStatus === "UNKNOWN",
    isJoiningExistingJob: jobSnapshot?.isJoiningExistingJob ?? false,
    retry,
    isRetrying,
    beginFollowingJob,
    staleRegenerationDetected,
    stalePollAsyncJobDetected,
    staleDetected: staleRegenerationDetected || stalePollAsyncJobDetected,
    isAsyncBootstrapping,
    isAwaitingPollPayload,
    hasDisplayableCompletedReport,
  };
}
