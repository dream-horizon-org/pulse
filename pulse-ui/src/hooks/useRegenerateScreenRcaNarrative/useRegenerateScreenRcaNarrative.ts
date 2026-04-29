import { useMutation, useQueryClient } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import {
  GET_RCA_JOB_ROUTE,
  GET_SCREEN_ROOT_CAUSE_ROUTE,
  POST_RCA_REPORT_ROUTE,
  POST_SCREEN_RCA_NARRATIVE_ROUTE,
} from "../../constants/API";
import {
  getJobIdFromRcaPostResponse,
  unwrapRcaJobApiBody,
  unwrapRcaReportPostApiBody,
} from "../../helpers/rcaResponseUnwrap";
import { makeRequest } from "../../helpers/makeRequest";
import type { ApiResponse } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import {
  RCA_JOB_POLL_MS,
  RCA_TYPE,
} from "../../screens/CriticalInteractionDetails/components/RootCause/RootCause.constants";
import { normalizeRcaJobStatus } from "../useGetRcaReport/useGetRcaReport";
import type {
  RcaJobResponse,
  RcaReportResponse,
} from "../useGetRcaReport/useGetRcaReport.interface";
import type { ScreenRcaReportApiResponse } from "../useGetScreenRcaNarrative/useGetScreenRcaNarrative.interface";
import type { UseRegenerateScreenRcaNarrativeParams } from "./useRegenerateScreenRcaNarrative.interface";

const RCA_HTTP_ACCEPTED = 202;
const RCA_HTTP_OK = 200;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function buildProjectHeaders(projectId: string): Record<string, string> {
  const headers: Record<string, string> = {};
  const trimmed = String(projectId).trim();
  if (trimmed !== "") {
    headers["X-Project-ID"] = trimmed;
  }
  return headers;
}

async function postScreenRcaRegenerateJob(
  params: UseRegenerateScreenRcaNarrativeParams,
): Promise<ApiResponse<RcaReportResponse | RcaJobResponse>> {
  const apiBaseUrl = getApiBaseUrl();
  const url = `${apiBaseUrl}${POST_RCA_REPORT_ROUTE.apiPath}`;
  const body = {
    rcaType: RCA_TYPE.SCREEN,
    entityKey: String(params.screenName).trim(),
    date: String(params.anchorDate).trim(),
    start: String(params.windowStartIso).trim(),
    end: String(params.windowEndIso).trim(),
    regenerate: true,
  };
  const raw = await makeRequest<RcaReportResponse | RcaJobResponse>({
    url,
    init: {
      method: POST_RCA_REPORT_ROUTE.method,
      body: JSON.stringify(body),
      headers: buildProjectHeaders(params.projectId),
    },
    unwrapped: true,
  });
  return unwrapRcaReportPostApiBody(raw);
}

async function getRcaJob(
  jobId: string,
  projectId: string,
): Promise<ApiResponse<RcaJobResponse>> {
  const apiBaseUrl = getApiBaseUrl();
  const url = `${apiBaseUrl}${GET_RCA_JOB_ROUTE.apiPath(jobId)}`;
  const raw = await makeRequest<RcaJobResponse>({
    url,
    init: {
      method: GET_RCA_JOB_ROUTE.method,
      headers: buildProjectHeaders(projectId),
    },
    unwrapped: true,
  });
  return unwrapRcaJobApiBody(raw);
}

async function pollRcaJobUntilTerminal(
  jobId: string,
  projectId: string,
): Promise<ApiResponse<RcaJobResponse>> {
  for (;;) {
    const res = await getRcaJob(jobId, projectId);
    const st = normalizeRcaJobStatus(res.data?.status);
    if (st === "COMPLETED" || st === "FAILED" || st === "UNKNOWN") {
      return res;
    }
    await sleep(RCA_JOB_POLL_MS);
  }
}

/**
 * Async RCA job: recomputes screen tabular root cause, runs pulse_ai narrative, caches MySQL — same
 * queue as interaction {@code POST /v1/ai/rca/report} + poll {@code GET /v1/ai-rca/job/{id}}.
 */
export function useRegenerateScreenRcaNarrative() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (
      params: UseRegenerateScreenRcaNarrativeParams,
    ): Promise<ApiResponse<ScreenRcaReportApiResponse>> => {
      const post = await postScreenRcaRegenerateJob(params);
      if (post.status === RCA_HTTP_OK && post.data != null) {
        const d = post.data as RcaReportResponse;
        return {
          status: RCA_HTTP_OK,
          data: {
            report: d.report as unknown as ScreenRcaReportApiResponse["report"],
            cached: d.cached ?? true,
            cachedAt:
              typeof d.cachedAt === "string"
                ? d.cachedAt
                : d.cachedAt != null
                  ? String(d.cachedAt)
                  : null,
          },
          error: null,
        };
      }
      if (post.status !== RCA_HTTP_ACCEPTED) {
        throw new Error(
          post.error?.message?.trim() ?? "Unexpected response from server",
        );
      }
      const jobId = getJobIdFromRcaPostResponse(post);
      if (jobId == null || jobId === "") {
        throw new Error("Missing job id from server");
      }
      const polled = await pollRcaJobUntilTerminal(jobId, params.projectId);
      if (polled.error != null || polled.data == null) {
        throw new Error(
          polled.error?.message?.trim() ?? "Failed to load job status",
        );
      }
      const st = normalizeRcaJobStatus(polled.data.status);
      if (st === "FAILED") {
        const msg = polled.data.errorMessage?.trim();
        throw new Error(
          msg !== "" && msg != null ? msg : "Report generation failed",
        );
      }
      if (st !== "COMPLETED") {
        throw new Error(`Unexpected job status: ${String(polled.data.status)}`);
      }
      if (polled.data.report == null) {
        throw new Error("Report not ready yet; please retry.");
      }
      return {
        status: RCA_HTTP_OK,
        data: {
          report: polled.data
            .report as unknown as ScreenRcaReportApiResponse["report"],
          cached: polled.data.cached ?? true,
          cachedAt:
            polled.data.cachedAt != null ? String(polled.data.cachedAt) : null,
        },
        error: null,
      };
    },
    onSuccess: (_data, variables) => {
      const name = String(variables.screenName).trim();
      const proj = String(variables.projectId).trim();
      const anchor = String(variables.anchorDate).trim();
      const asOf = String(variables.asOfIso).trim();
      const ws = String(variables.windowStartIso).trim();
      const we = String(variables.windowEndIso).trim();
      void queryClient.invalidateQueries({
        queryKey: [GET_SCREEN_ROOT_CAUSE_ROUTE.key, name, proj, anchor, asOf],
      });
      void queryClient.invalidateQueries({
        queryKey: [
          POST_SCREEN_RCA_NARRATIVE_ROUTE.key,
          name,
          proj,
          anchor,
          ws,
          we,
        ],
      });
    },
    onError: (e) => {
      notifications.show({
        title: "Regenerate failed",
        message: e instanceof Error ? e.message : "Something went wrong.",
        color: "red",
      });
    },
  });
}
