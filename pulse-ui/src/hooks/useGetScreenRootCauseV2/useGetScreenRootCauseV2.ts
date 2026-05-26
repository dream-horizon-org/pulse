import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import type { ApiResponse } from "../../helpers/makeRequest";
import { GET_SCREEN_ROOT_CAUSE_V2_ROUTE } from "../../constants/API";
import type {
  ScreenRcaV2Data,
  UseGetScreenRootCauseV2Params,
} from "./useGetScreenRootCauseV2.interface";

function buildScreenRootCauseV2Url(
  screenName: string,
  windowEndIso: string | undefined,
): string {
  const base = getApiBaseUrl().replace(/\/$/, "");
  const encoded = encodeURIComponent(screenName);
  const path = `${GET_SCREEN_ROOT_CAUSE_V2_ROUTE.apiPathPrefix}/${encoded}${GET_SCREEN_ROOT_CAUSE_V2_ROUTE.apiPathSuffix}`;
  const params = new URLSearchParams();
  if (windowEndIso && windowEndIso.trim() !== "") {
    params.set("windowEnd", windowEndIso.trim());
  }
  const q = params.toString();
  return `${base}${path}${q ? `?${q}` : ""}`;
}

export function useGetScreenRootCauseV2({
  screenName,
  windowEndIso,
  projectId,
  enabled = true,
}: UseGetScreenRootCauseV2Params) {
  const trimmedName = screenName != null ? String(screenName).trim() : "";
  const trimmedProject = projectId != null ? String(projectId).trim() : "";
  const we = windowEndIso != null ? String(windowEndIso).trim() : "";

  return useQuery({
    queryKey: [GET_SCREEN_ROOT_CAUSE_V2_ROUTE.key, trimmedName, trimmedProject, we],
    queryFn: async (): Promise<ApiResponse<ScreenRcaV2Data>> => {
      if (!trimmedName) {
        return {
          data: null,
          error: { code: "400", message: "Screen name is required", cause: "" },
          status: 400,
        };
      }
      const url = buildScreenRootCauseV2Url(trimmedName, we || undefined);
      const headers: Record<string, string> = {};
      if (trimmedProject !== "") {
        headers["X-Project-ID"] = trimmedProject;
      }
      return makeRequest<ScreenRcaV2Data>({
        url,
        init: { method: GET_SCREEN_ROOT_CAUSE_V2_ROUTE.method, headers },
      });
    },
    enabled: enabled && trimmedName !== "" && trimmedProject !== "" && we !== "",
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * Math.pow(2, attemptIndex), 10000),
    refetchInterval: 2000,
    refetchIntervalInBackground: true,
    refetchOnWindowFocus: true,
  });
}
