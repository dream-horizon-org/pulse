import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import type { ApiResponse } from "../../helpers/makeRequest/makeRequest.interface";
import type { WebVitalsByScreenResponse } from "../../screens/WebVitals/WebVitals.interface";
import type { WebVitalsByScreenWire } from "../../screens/WebVitals/WebVitalsWire.types";
import { normalizeWebVitalsByScreenResponse } from "../../screens/WebVitals/normalizeWebVitalsApi";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";
import { UseWebVitalsByScreenParams } from "./useWebVitalsByScreen.interface";

export const useWebVitalsByScreen = ({
  startTime,
  endTime,
  vitalName,
  enabled: enabledParam = true,
}: UseWebVitalsByScreenParams) => {
  const enabled = useProjectQueryEnabled() && enabledParam;
  const route = API_ROUTES.GET_WEB_VITALS_BY_SCREEN;

  const queryParams = {
    startTime,
    endTime,
    vitalName,
  };

  const queryString = new URLSearchParams(
    Object.fromEntries(
      Object.entries(queryParams).map(([k, v]) => [k, String(v)]),
    ),
  ).toString();
  const url = `${API_BASE_URL}${route.apiPath}?${queryString}`;

  return useQuery({
    queryKey: [route.key, startTime, endTime, vitalName, enabledParam],
    queryFn: async (): Promise<ApiResponse<WebVitalsByScreenResponse>> => {
      const res = await makeRequest<WebVitalsByScreenWire>({
        url,
        init: {
          method: route.method,
        },
      });
      return {
        ...res,
        data: normalizeWebVitalsByScreenResponse(res.data ?? null),
      };
    },
    enabled,
    refetchOnWindowFocus: false,
  });
};
