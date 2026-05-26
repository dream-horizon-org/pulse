import { useMutation, useQueryClient } from "@tanstack/react-query";
import { POST_RCA_REPORT_ROUTE } from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import { isValidRcaDateParam } from "../../helpers/rcaRequestUtils";
import {
  getJobIdFromRcaPostResponse,
  unwrapRcaReportPostApiBody,
} from "../../helpers/rcaResponseUnwrap";
import { getRcaApiBaseUrl } from "../../utils";
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
      rcaType = RCA_TYPE.INTERACTION,
      windowStartIso,
      windowEndIso,
    }: UseRegenerateRcaReportParams) => {
      const apiBaseUrl = getRcaApiBaseUrl(rcaType);
      const url = `${apiBaseUrl}${POST_RCA_REPORT_ROUTE.apiPath}`;
      const body: Record<string, string | boolean> = {
        rcaType,
        entityKey,
        regenerate: true,
      };
      if (isValidRcaDateParam(date)) {
        body.date = date;
      }
      const start = windowStartIso != null ? String(windowStartIso).trim() : "";
      const end = windowEndIso != null ? String(windowEndIso).trim() : "";
      if (start !== "" && end !== "") {
        body.start = start;
        body.end = end;
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
      const trimmedProjectId = String(variables.projectId ?? "").trim();
      const partialPostKey = [
        POST_RCA_REPORT_ROUTE.key,
        variables.entityKey,
        variables.date ?? null,
        variables.rcaType ?? RCA_TYPE.INTERACTION,
        trimmedProjectId,
      ];

      if (data.status === 200) {
        queryClient.invalidateQueries({
          queryKey: [...partialPostKey],
          refetchType: "all",
        });
        return;
      }
      if (data.status === 202 && getJobIdFromRcaPostResponse(data) == null) {
        queryClient.invalidateQueries({
          queryKey: [...partialPostKey],
          refetchType: "all",
        });
      }
    },
  });
};
