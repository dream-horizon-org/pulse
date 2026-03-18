import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import { ApiResponse } from "../../helpers/makeRequest";
import type {
  RcaReportResponse,
  UseGetRcaReportParams,
} from "./useGetRcaReport.interface";

/**
 * POST /v1/ai/rca/report — AI RCA report (mock or real backend).
 * API_ROUTES is read inside the hook to avoid circular init with Constants.ts
 * (Constants imports screens that import this hook).
 */
export const useGetRcaReport = ({
  interactionName,
  date,
  enabled = true,
  projectId,
}: UseGetRcaReportParams) => {
  const postRcaReportRoute = API_ROUTES.POST_RCA_REPORT;
  return useQuery({
    queryKey: [postRcaReportRoute.key, interactionName, date, projectId],
    queryFn: async (): Promise<ApiResponse<RcaReportResponse>> => {
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
      const url = `${API_BASE_URL}${postRcaReportRoute.apiPath}`;
      const validDate =
        date && date !== "Invalid Date" && /^\d{4}-\d{2}-\d{2}$/.test(date);
      const body: { interactionName: string; date?: string } = {
        interactionName,
      };
      if (validDate) {
        body.date = date;
      }
      const headers: Record<string, string> = {};
      const hasProjectId = projectId && String(projectId).trim() !== "";
      if (hasProjectId) {
        headers["X-Project-ID"] = String(projectId).trim();
      }
      return makeRequest<RcaReportResponse>({
        url,
        init: {
          method: postRcaReportRoute.method,
          body: JSON.stringify(body),
          headers: { ...headers },
        },
        unwrapped: true,
      });
    },
    enabled: enabled && !!interactionName && !!projectId,
    retry: false,
  });
};
