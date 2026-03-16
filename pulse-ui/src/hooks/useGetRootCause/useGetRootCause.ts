import { useQuery } from "@tanstack/react-query";
import {
  API_BASE_URL,
  GET_INTERACTION_ROOT_CAUSE_ROUTE,
} from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import { ApiResponse } from "../../helpers/makeRequest";
import {
  RootCauseResponse,
  UseGetRootCauseParams,
} from "./useGetRootCause.interface";

/**
 * Fetches root cause analysis for an interaction.
 * GET /v1/interactions/:name/root-cause with optional ?date=YYYY-MM-DD.
 * Uses default 60s timeout (makeRequest/withTimeout). Project/tenant sent via existing auth headers.
 */
export function useGetRootCause({
  interactionName,
  date,
  enabled = true,
}: UseGetRootCauseParams) {
  return useQuery({
    queryKey: [GET_INTERACTION_ROOT_CAUSE_ROUTE.key, interactionName, date],
    queryFn: async (): Promise<ApiResponse<RootCauseResponse>> => {
      if (!interactionName) {
        return {
          data: null,
          error: {
            code: "400",
            message: "Interaction name required",
            cause: "",
          },
          status: 400,
        };
      }
      const path = `${API_BASE_URL}${GET_INTERACTION_ROOT_CAUSE_ROUTE.apiPath}/${encodeURIComponent(interactionName)}/root-cause`;
      const url = date ? `${path}?date=${encodeURIComponent(date)}` : path;
      return makeRequest<RootCauseResponse>({
        url,
        init: {
          method: GET_INTERACTION_ROOT_CAUSE_ROUTE.method,
        },
      });
    },
    enabled: enabled && !!interactionName,
    retry: false,
  });
}
