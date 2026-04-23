import { useMutation, useQueryClient } from "@tanstack/react-query";
import { POST_RCA_REPORT_ROUTE } from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import { isValidRcaDateParam } from "../../helpers/rcaRequestUtils";
import {
  getJobIdFromRcaPostResponse,
  unwrapRcaReportPostApiBody,
} from "../../helpers/rcaResponseUnwrap";
import { getApiBaseUrl } from "../../utils";
import { RCA_TYPE } from "../../screens/CriticalInteractionDetails/components/RootCause/RootCause.constants";
import type {
  RcaJobResponse,
  RcaReportResponse,
} from "../useGetRcaReport/useGetRcaReport.interface";
import type { UseRegenerateRcaReportParams } from "./useRegenerateRcaReport.interface";

/**
 * Recomputes ClickHouse segments and regenerates the AI RCA report for the key.
 * POST /v1/ai/rca/report with { entityKey, date?, regenerate: true }.
 * Bodies are unwrapped with {@link unwrapRcaReportPostApiBody}; on 202 use
 * {@link getJobIdFromRcaPostResponse} from `rcaResponseUnwrap` to read `jobId`.
 */
export const useRegenerateRcaReport = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      entityKey,
      date,
      projectId,
    }: UseRegenerateRcaReportParams) => {
      const apiBaseUrl = getApiBaseUrl();
      const url = `${apiBaseUrl}${POST_RCA_REPORT_ROUTE.apiPath}`;
      const body: {
        rcaType: string;
        entityKey: string;
        date?: string;
        regenerate: boolean;
      } = {
        rcaType: RCA_TYPE.INTERACTION,
        entityKey,
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
      const raw = await makeRequest<RcaReportResponse | RcaJobResponse>({
        url,
        init: {
          method: POST_RCA_REPORT_ROUTE.method,
          body: JSON.stringify(body),
          headers: { ...headers },
        },
        unwrapped: true,
      });
      return unwrapRcaReportPostApiBody(raw);
    },
    onSuccess: (data, variables) => {
      if (data.status === 200) {
        queryClient.invalidateQueries({
          queryKey: [
            POST_RCA_REPORT_ROUTE.key,
            variables.entityKey,
            variables.date ?? null,
            variables.projectId,
          ],
          refetchType: "all",
        });
        return;
      }
      if (data.status === 202 && getJobIdFromRcaPostResponse(data) == null) {
        queryClient.invalidateQueries({
          queryKey: [
            POST_RCA_REPORT_ROUTE.key,
            variables.entityKey,
            variables.date ?? null,
            variables.projectId,
          ],
          refetchType: "all",
        });
      }
    },
  });
};
