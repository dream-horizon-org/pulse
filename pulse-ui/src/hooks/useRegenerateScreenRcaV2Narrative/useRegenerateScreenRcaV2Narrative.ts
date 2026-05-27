import { useMutation, useQueryClient } from "@tanstack/react-query";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { notifications } from "@mantine/notifications";
import { POST_RCA_REPORT_ROUTE } from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import type { ApiResponse } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import { RCA_TYPE } from "../../screens/CriticalInteractionDetails/components/RootCause/RootCause.constants";
import type {
  ScreenRcaV2JobResponse,
  ScreenRcaV2ReportApiResponse,
} from "../useGetScreenRcaV2Narrative/useGetScreenRcaV2Narrative.interface";

dayjs.extend(utc);

export interface RegenerateScreenRcaV2Params {
  screenName: string;
  windowEndIso: string;
  windowStartIso: string;
  projectId: string;
}

function reportDateFromWindowEnd(windowEndIso: string): string {
  const endMs = Date.parse(windowEndIso);
  if (Number.isNaN(endMs)) {
    return windowEndIso;
  }
  return dayjs.utc(endMs).format("YYYY-MM-DD");
}

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

export function getJobIdFromScreenV2PostResponse(
  res: ApiResponse<ScreenRcaV2ReportApiResponse | ScreenRcaV2JobResponse>,
): string | null {
  const { data } = unwrapScreenV2PostApiBody(res);
  if (data != null && typeof data === "object" && "jobId" in data) {
    const id = String((data as ScreenRcaV2JobResponse).jobId ?? "").trim();
    return id !== "" ? id : null;
  }
  return null;
}

/**
 * Regenerates Screen RCA v2 narrative.
 * POST /v1/ai/rca/report with regenerate: true; on 202 caller follows job via beginFollowingJob.
 * Mirrors {@link useRegenerateRcaReport} — no inline poll loop in the mutation.
 */
export function useRegenerateScreenRcaV2Narrative() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (params: RegenerateScreenRcaV2Params) => {
      const apiBaseUrl = getApiBaseUrl();
      const headers: Record<string, string> = { "Content-Type": "application/json" };
      const trimmedProjectId = params.projectId.trim();
      if (trimmedProjectId !== "") {
        headers["X-Project-ID"] = trimmedProjectId;
      }
      const body = {
        rcaType: RCA_TYPE.SCREEN_V2,
        entityKey: params.screenName.trim(),
        date: reportDateFromWindowEnd(params.windowEndIso),
        start: params.windowStartIso,
        end: params.windowEndIso,
        regenerate: true,
      };
      const raw = await makeRequest<ScreenRcaV2ReportApiResponse | ScreenRcaV2JobResponse>({
        url: `${apiBaseUrl}${POST_RCA_REPORT_ROUTE.apiPath}`,
        init: {
          method: POST_RCA_REPORT_ROUTE.method,
          body: JSON.stringify(body),
          headers,
        },
        unwrapped: true,
      });
      return unwrapScreenV2PostApiBody(raw);
    },
    onSuccess: (data, variables) => {
      if (data.status === 200) {
        void queryClient.invalidateQueries({
          queryKey: [
            POST_RCA_REPORT_ROUTE.key,
            RCA_TYPE.SCREEN_V2,
            variables.screenName.trim(),
            variables.projectId.trim(),
          ],
          refetchType: "all",
        });
        return;
      }
      if (data.status === 202 && getJobIdFromScreenV2PostResponse(data) == null) {
        void queryClient.invalidateQueries({
          queryKey: [
            POST_RCA_REPORT_ROUTE.key,
            RCA_TYPE.SCREEN_V2,
            variables.screenName.trim(),
            variables.projectId.trim(),
          ],
          refetchType: "all",
        });
      }
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
