import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import {
  GetAnalyticsReportIdResponse,
  GetAnalyticsReportIdRequestBodyParams,
} from "./createReport.interface";

export const createReport = async (
  requestBody: GetAnalyticsReportIdRequestBodyParams,
) => {
  return makeRequest<GetAnalyticsReportIdResponse>({
    url: `${API_BASE_URL}${API_ROUTES.CREATE_ANALYSIS_REPORT.apiPath}`,
    init: {
      method: API_ROUTES.CREATE_ANALYSIS_REPORT.method,
      body: JSON.stringify(requestBody),
    },
  });
};
