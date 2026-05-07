import { useQuery } from "@tanstack/react-query";
import { POST_SESSION_RCA_NARRATIVE_ROUTE } from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import type { ApiResponse } from "../../helpers/makeRequest";
import type {
  SessionRcaReportApiResponse,
  UseGetSessionRcaNarrativeParams,
} from "./useGetSessionRcaNarrative.interface";
import type { SessionRcaData } from "../useGetSessionRca";

export function buildSessionRcaPayloadForAi(
  data: SessionRcaData,
): Record<string, unknown> {
  return {
    baseline: data.baseline ?? {},
    segments: (data.segments ?? []).map((s) => ({
      label: s.label,
      dimensions: s.dimensions ?? {},
      metrics: s.metrics ?? {},
      deltas: s.deltas ?? {},
    })),
    mode: data.mode ?? null,
    cachedAt: data.cachedAt ?? null,
    everythingGood: data.everythingGood ?? null,
    noDataAvailable: data.noDataAvailable ?? null,
    message: data.message ?? null,
  };
}

export function useGetSessionRcaNarrative({
  anchorDate,
  asOfIso,
  projectId,
  rootCauseData,
  enabled = true,
}: UseGetSessionRcaNarrativeParams) {
  const d = anchorDate != null ? String(anchorDate).trim() : "";
  const asOf = asOfIso != null ? String(asOfIso).trim() : "";
  const pid = projectId != null ? String(projectId).trim() : "";

  return useQuery({
    queryKey: [
      POST_SESSION_RCA_NARRATIVE_ROUTE.key,
      pid,
      d,
      asOf,
      rootCauseData?.cachedAt ?? "",
      rootCauseData?.everythingGood ?? "",
      rootCauseData?.segments?.length ?? 0,
    ],
    queryFn: async (): Promise<ApiResponse<SessionRcaReportApiResponse>> => {
      if (!rootCauseData) {
        return { data: null, error: { code: "400", message: "Root cause data required", cause: "" }, status: 400 };
      }
      const url = `${getApiBaseUrl()}${POST_SESSION_RCA_NARRATIVE_ROUTE.apiPath}`;
      const body: Record<string, unknown> = {
        rootCausePayload: buildSessionRcaPayloadForAi(rootCauseData),
      };
      if (d !== "") body.date = d;
      if (asOf !== "") body.asOf = asOf;
      return makeRequest<SessionRcaReportApiResponse>({
        url,
        init: {
          method: POST_SESSION_RCA_NARRATIVE_ROUTE.method,
          body: JSON.stringify(body),
          headers: pid !== "" ? { "X-Project-ID": pid } : {},
        },
        unwrapped: true,
      });
    },
    enabled: enabled && pid !== "" && d !== "" && rootCauseData != null,
    retry: false,
  });
}
