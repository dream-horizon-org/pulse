import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import type { ApiResponse } from "../../helpers/makeRequest/makeRequest.interface";
import type { WebVitalsSummaryResponse } from "../../screens/WebVitals/WebVitals.interface";
import type { WebVitalsSummaryWire } from "../../screens/WebVitals/WebVitalsWire.types";
import { normalizeWebVitalsSummaryResponse } from "../../screens/WebVitals/normalizeWebVitalsApi";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";
import { removeUndefinedOrNullValues } from "../../helpers/queryParams";
import { UseWebVitalsSummaryParams } from "./useWebVitalsSummary.interface";

export const useWebVitalsSummary = ({
  startTime,
  endTime,
  screenName,
}: UseWebVitalsSummaryParams) => {
  const enabled = useProjectQueryEnabled();
  const route = API_ROUTES.GET_WEB_VITALS_SUMMARY;

  const queryParams = removeUndefinedOrNullValues({
    startTime,
    endTime,
    screenName,
  });

  const queryString = new URLSearchParams(
    Object.fromEntries(
      Object.entries(queryParams).map(([k, v]) => [k, String(v)]),
    ),
  ).toString();
  const url = `${API_BASE_URL}${route.apiPath}?${queryString}`;

  return useQuery({
    queryKey: [route.key, startTime, endTime, screenName],
    queryFn: async (): Promise<ApiResponse<WebVitalsSummaryResponse>> => {
      const res = await makeRequest<WebVitalsSummaryWire>({
        url,
        init: {
          method: route.method,
        },
      });
      return {
        ...res,
        data: normalizeWebVitalsSummaryResponse(res.data ?? null),
      };
    },
    enabled,
    refetchOnWindowFocus: false,
  });
};
