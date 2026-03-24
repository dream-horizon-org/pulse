import { useMutation, useQueryClient } from "@tanstack/react-query";
import { API_BASE_URL, POST_RCA_REPORT_ROUTE } from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import type { RcaReportResponse } from "../useGetRcaReport/useGetRcaReport.interface";
import type { UseRegenerateRcaReportParams } from "./useRegenerateRcaReport.interface";

const isValidRcaDateParam = (date: string | null | undefined): date is string =>
  !!date && date !== "Invalid Date" && /^\d{4}-\d{2}-\d{2}$/.test(date);

/**
 * Recomputes ClickHouse segments and regenerates the AI RCA report for the key.
 * POST /v1/ai/rca/report with { interactionName, date?, regenerate: true }.
 */
export const useRegenerateRcaReport = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      interactionName,
      date,
      projectId,
    }: UseRegenerateRcaReportParams) => {
      const url = `${API_BASE_URL}${POST_RCA_REPORT_ROUTE.apiPath}`;
      const body: {
        interactionName: string;
        date?: string;
        regenerate: boolean;
      } = {
        interactionName,
        regenerate: true,
      };
      if (isValidRcaDateParam(date)) {
        body.date = date;
      }
      const headers: Record<string, string> = {};
      const trimmedProjectId = String(projectId).trim();
      if (trimmedProjectId !== "") {
        headers["X-Project-ID"] = trimmedProjectId;
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
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: [
          POST_RCA_REPORT_ROUTE.key,
          variables.interactionName,
          variables.date ?? null,
          variables.projectId,
        ],
      });
    },
  });
};
