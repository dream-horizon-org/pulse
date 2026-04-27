import { useQuery } from "@tanstack/react-query";
import { POST_SCREEN_RCA_NARRATIVE_ROUTE } from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import type { ApiResponse } from "../../helpers/makeRequest";
import type {
  ScreenRcaReportApiResponse,
  UseGetScreenRcaNarrativeParams,
} from "./useGetScreenRcaNarrative.interface";
import type { ScreenRootCauseData } from "../useGetScreenRootCause";

/** Builds JSON body field `rootCausePayload` for pulse_ai `RootCausePayloadSchema`. */
export function buildScreenRootCausePayloadForAi(
  data: ScreenRootCauseData,
): Record<string, unknown> {
  const cachedAt = data.cachedAt;
  return {
    baseline: data.baseline ?? {},
    segments: (data.segments ?? []).map((s) => ({
      label: s.label,
      dimensions: s.dimensions ?? {},
      metrics: s.metrics ?? {},
      deltas: s.deltas ?? {},
    })),
    mode: data.mode ?? null,
    cachedAt:
      typeof cachedAt === "string"
        ? cachedAt
        : cachedAt != null
          ? String(cachedAt)
          : null,
    everythingGood: data.everythingGood ?? null,
    noDataAvailable: data.noDataAvailable ?? null,
    message: data.message ?? null,
  };
}

/**
 * POST /v1/ai/rca/screen-report with embedded root-cause tabular JSON.
 */
export function useGetScreenRcaNarrative({
  screenName,
  windowStartIso,
  windowEndIso,
  projectId,
  rootCauseData,
  enabled = true,
}: UseGetScreenRcaNarrativeParams) {
  const trimmedName = screenName != null ? String(screenName).trim() : "";
  const trimmedProject = projectId != null ? String(projectId).trim() : "";
  const ws = windowStartIso != null ? String(windowStartIso).trim() : "";
  const we = windowEndIso != null ? String(windowEndIso).trim() : "";

  return useQuery({
    queryKey: [
      POST_SCREEN_RCA_NARRATIVE_ROUTE.key,
      trimmedName,
      trimmedProject,
      ws,
      we,
      rootCauseData?.cachedAt ?? "",
      rootCauseData?.everythingGood ?? "",
      rootCauseData?.segments?.length ?? 0,
    ],
    queryFn: async (): Promise<ApiResponse<ScreenRcaReportApiResponse>> => {
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
      if (!rootCauseData) {
        return {
          data: null,
          error: {
            code: "400",
            message: "Root cause data required",
            cause: "",
          },
          status: 400,
        };
      }
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
    enabled:
      enabled &&
      trimmedName !== "" &&
      trimmedProject !== "" &&
      ws !== "" &&
      we !== "" &&
      rootCauseData != null,
    retry: false,
  });
}
