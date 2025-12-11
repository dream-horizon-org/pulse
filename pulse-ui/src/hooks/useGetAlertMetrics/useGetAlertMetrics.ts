import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { GetAlertMetricsResponse, GetAlertMetricsParams } from "./useGetAlertMetrics.interface";

export const useGetAlertMetrics = ({ scope }: GetAlertMetricsParams) => {
  const getAlertMetrics = API_ROUTES.GET_ALERT_METRICS;
  return useQuery({
    queryKey: [getAlertMetrics.key, scope],
    queryFn: async () => {
      if (!scope) {
        return { data: { scope: "", metrics: [] } } as { data: GetAlertMetricsResponse };
      }
      return makeRequest<GetAlertMetricsResponse>({
        url: `${API_BASE_URL}${getAlertMetrics.apiPath}?scope=${scope}`,
        init: {
          method: getAlertMetrics.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    refetchInterval: false,
    enabled: !!scope,
  });
};

