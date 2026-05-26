import { useMutation, useQueryClient } from "@tanstack/react-query";
import { notifications } from "@mantine/notifications";
import { GET_RCA_JOB_ROUTE, POST_RCA_REPORT_ROUTE } from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import {
  RCA_JOB_POLL_MS,
  RCA_TYPE,
} from "../../screens/CriticalInteractionDetails/components/RootCause/RootCause.constants";
import { normalizeRcaJobStatus } from "../useGetRcaReport";
import type { ScreenRcaV2JobResponse } from "../useGetScreenRcaV2Narrative";

export interface RegenerateScreenRcaV2Params {
  screenName: string;
  windowEndIso: string;
  windowStartIso: string;
  projectId: string;
}

async function pollUntilDone(
  jobId: string,
  projectId: string,
  apiBaseUrl: string,
): Promise<void> {
  const headers: Record<string, string> = {};
  if (projectId.trim() !== "") {
    headers["X-Project-ID"] = projectId.trim();
  }
  for (;;) {
    await new Promise<void>((resolve) => window.setTimeout(resolve, RCA_JOB_POLL_MS));
    const jobUrl = `${apiBaseUrl}${GET_RCA_JOB_ROUTE.apiPath(jobId)}`;
    const result = await makeRequest<ScreenRcaV2JobResponse>({
      url: jobUrl,
      init: { method: GET_RCA_JOB_ROUTE.method, headers },
      unwrapped: true,
    });
    const status = normalizeRcaJobStatus(result.data?.status ?? null);
    if (status === "COMPLETED") return;
    if (status === "FAILED" || status === "UNKNOWN") {
      throw new Error(result.data?.errorMessage ?? "Report generation failed");
    }
  }
}

export function useRegenerateScreenRcaV2Narrative() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (params: RegenerateScreenRcaV2Params): Promise<void> => {
      const apiBaseUrl = getApiBaseUrl();
      const headers: Record<string, string> = { "Content-Type": "application/json" };
      if (params.projectId.trim() !== "") {
        headers["X-Project-ID"] = params.projectId.trim();
      }
      const body = {
        rcaType: RCA_TYPE.SCREEN_V2,
        entityKey: params.screenName.trim(),
        date: params.windowEndIso,
        start: params.windowStartIso,
        end: params.windowEndIso,
        regenerate: true,
      };
      const result = await makeRequest<ScreenRcaV2JobResponse>({
        url: `${apiBaseUrl}${POST_RCA_REPORT_ROUTE.apiPath}`,
        init: { method: POST_RCA_REPORT_ROUTE.method, body: JSON.stringify(body), headers },
        unwrapped: true,
      });

      if (result.status === 200) return; // already cached — nothing to poll
      if (result.status === 202) {
        const jobId = result.data?.jobId;
        if (jobId) {
          await pollUntilDone(jobId, params.projectId, apiBaseUrl);
        }
        return;
      }
      throw new Error(result.error?.message ?? "Failed to start report regeneration");
    },
    onSuccess: (_data, variables) => {
      // Invalidate so useGetScreenRcaV2Narrative re-fetches (will get 200 cache hit)
      void queryClient.invalidateQueries({
        queryKey: [
          POST_RCA_REPORT_ROUTE.key,
          RCA_TYPE.SCREEN_V2,
          variables.screenName.trim(),
          variables.projectId.trim(),
          variables.windowEndIso,
          variables.windowStartIso,
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
