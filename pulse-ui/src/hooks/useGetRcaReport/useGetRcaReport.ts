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
  tenantContext,
}: UseGetRcaReportParams) => {
  const postRcaReportRoute = API_ROUTES.POST_RCA_REPORT;
  const tenantContextKey = tenantContext
    ? [
        tenantContext.errorRatePercent,
        tenantContext.poorUsersPercent,
        tenantContext.apdex ?? "",
        tenantContext.p50Ms ?? "",
        tenantContext.p95Ms ?? "",
      ].join("|")
    : "";
  return useQuery({
    queryKey: [
      postRcaReportRoute.key,
      interactionName,
      date,
      projectId,
      tenantContextKey,
    ],
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
      const body: {
        interactionName: string;
        date?: string;
        errorRatePercent?: number;
        poorUsersPercent?: number;
        apdex?: number;
        p50Ms?: number;
        p95Ms?: number;
      } = {
        interactionName,
      };
      if (validDate) {
        body.date = date;
      }
      if (tenantContext) {
        body.errorRatePercent = tenantContext.errorRatePercent;
        body.poorUsersPercent = tenantContext.poorUsersPercent;
        if (tenantContext.apdex != null) {
          body.apdex = tenantContext.apdex;
        }
        if (tenantContext.p50Ms != null) {
          body.p50Ms = tenantContext.p50Ms;
        }
        if (tenantContext.p95Ms != null) {
          body.p95Ms = tenantContext.p95Ms;
        }
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
