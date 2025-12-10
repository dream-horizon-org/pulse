import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  GetAlertEvaluationHistoryParams,
  GetAlertEvaluationHistoryResponse,
} from "./useGetAlertEvaluationHistory.interface";

export const useGetAlertEvaluationHistory = ({
  alertId,
}: GetAlertEvaluationHistoryParams) => {
  const getAlertEvaluationHistory = API_ROUTES.GET_ALERT_EVALUATION_HISTORY;

  return useQuery({
    queryKey: [getAlertEvaluationHistory.key, alertId],
    queryFn: async () => {
      if (alertId === null || alertId === "") {
        return {} as ApiResponse<GetAlertEvaluationHistoryResponse>;
      }

      return makeRequest<GetAlertEvaluationHistoryResponse>({
        url: `${API_BASE_URL}${getAlertEvaluationHistory.apiPath}`.replace(
          "{id}",
          alertId,
        ),
        init: {
          method: getAlertEvaluationHistory.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    refetchInterval: false,
    enabled: false,
  });
};


