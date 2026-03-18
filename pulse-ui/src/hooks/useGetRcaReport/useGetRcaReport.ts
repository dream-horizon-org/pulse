import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, POST_RCA_REPORT_ROUTE } from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import { ApiResponse } from "../../helpers/makeRequest";
import type {
  RcaReportResponse,
  UseGetRcaReportParams,
} from "./useGetRcaReport.interface";

/**
 * Fetches the AI-generated RCA report for an interaction.
 * POST /v1/ai/rca/report with body { interactionName, date? }.
 * Auth and X-Project-ID are added by makeRequest/buildAuthHeaders.
 * Response is raw JSON (unwrapped).
 */
export function useGetRcaReport({
  interactionName,
  date,
  enabled = true,
  projectId,
}: UseGetRcaReportParams) {
  return useQuery({
    queryKey: [POST_RCA_REPORT_ROUTE.key, interactionName, date, projectId],
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
      const url = `${API_BASE_URL}${POST_RCA_REPORT_ROUTE.apiPath}`;
      const validDate =
        date && date !== "Invalid Date" && /^\d{4}-\d{2}-\d{2}$/.test(date);
      const body: { interactionName: string; date?: string } = {
        interactionName,
      };
      if (validDate) {
        body.date = date;
      }
      const headers: Record<string, string> = {};
      if (projectId && String(projectId).trim() !== "") {
        headers["X-Project-ID"] = String(projectId).trim();
      }
      return makeRequest<RcaReportResponse>({
        url,
        init: {
          method: POST_RCA_REPORT_ROUTE.method,
          body: JSON.stringify(body),
          headers: { ...headers },
        },
        unwrapped: true,
      });
    },
    enabled: enabled && !!interactionName && !!projectId,
    retry: false,
  });
}
