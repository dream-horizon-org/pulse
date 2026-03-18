import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import { ApiResponse } from "../../helpers/makeRequest";
import type {
  RootCauseResponse,
  UseGetRootCauseParams,
} from "./useGetRootCause.interface";

/**
 * GET /v1/interactions/:name/root-cause with optional ?date=YYYY-MM-DD.
 * API_ROUTES read inside hook to avoid circular init with Constants.ts.
 */
export const useGetRootCause = ({
  interactionName,
  date,
  enabled = true,
  projectId,
}: UseGetRootCauseParams) => {
  const getRootCauseRoute = API_ROUTES.GET_INTERACTION_ROOT_CAUSE;
  return useQuery({
    queryKey: [getRootCauseRoute.key, interactionName, date, projectId],
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
      const path = `${API_BASE_URL}${getRootCauseRoute.apiPath}/${encodeURIComponent(interactionName)}/root-cause`;
      const validDate =
        date && date !== "Invalid Date" && /^\d{4}-\d{2}-\d{2}$/.test(date);
      const url = validDate ? `${path}?date=${encodeURIComponent(date)}` : path;
      const headers: Record<string, string> = {};
      const hasProjectId = projectId && String(projectId).trim() !== "";
      if (hasProjectId) {
        headers["X-Project-ID"] = String(projectId).trim();
      }
      return makeRequest<RootCauseResponse>({
        url,
        init: {
          method: getRootCauseRoute.method,
          ...(Object.keys(headers).length > 0 ? { headers } : {}),
        },
      });
    },
    enabled: enabled && !!interactionName,
    retry: false,
  });
};
