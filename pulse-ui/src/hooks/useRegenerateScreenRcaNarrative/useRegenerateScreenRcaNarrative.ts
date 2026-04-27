import { useMutation, useQueryClient } from "@tanstack/react-query";
import { POST_SCREEN_RCA_NARRATIVE_ROUTE } from "../../constants/API";
import { buildScreenRootCausePayloadForAi } from "../useGetScreenRcaNarrative/useGetScreenRcaNarrative";
import { makeRequest } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import type { ScreenRcaReportApiResponse } from "../useGetScreenRcaNarrative/useGetScreenRcaNarrative.interface";
import type { UseRegenerateScreenRcaNarrativeParams } from "./useRegenerateScreenRcaNarrative.interface";

/**
 * Skips MySQL narrative cache and forces a fresh AI screen narrative.
 * POST /v1/ai/rca/screen-report with regenerate: true (server strips before pulse_ai).
 */
export function useRegenerateScreenRcaNarrative() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      screenName,
      windowStartIso,
      windowEndIso,
      projectId,
      rootCauseData,
    }: UseRegenerateScreenRcaNarrativeParams) => {
      const trimmedName = String(screenName).trim();
      const trimmedProject = String(projectId).trim();
      const ws = String(windowStartIso).trim();
      const we = String(windowEndIso).trim();
      const apiBaseUrl = getApiBaseUrl();
      const url = `${apiBaseUrl}${POST_SCREEN_RCA_NARRATIVE_ROUTE.apiPath}`;
      const headers: Record<string, string> = {};
      if (trimmedProject !== "") {
        headers["X-Project-ID"] = trimmedProject;
      }
      const body = {
        screenName: trimmedName,
        start: ws !== "" ? ws : undefined,
        end: we !== "" ? we : undefined,
        rootCausePayload: buildScreenRootCausePayloadForAi(rootCauseData),
        regenerate: true,
      };
      return makeRequest<ScreenRcaReportApiResponse>({
        url,
        init: {
          method: POST_SCREEN_RCA_NARRATIVE_ROUTE.method,
          body: JSON.stringify(body),
          headers: { ...headers },
        },
        unwrapped: true,
      });
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: [
          POST_SCREEN_RCA_NARRATIVE_ROUTE.key,
          String(variables.screenName).trim(),
          String(variables.projectId).trim(),
          String(variables.windowStartIso).trim(),
          String(variables.windowEndIso).trim(),
        ],
      });
    },
  });
}
