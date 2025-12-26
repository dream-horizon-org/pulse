import { useMemo } from "react";
import { useQueries } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { GetAlertMetricsResponse } from "./useGetAlertMetrics.interface";

export type UseGetAllScopeMetricsParams = {
  scopeNames: string[];
};

export type UseGetAllScopeMetricsResult = {
  /** Metric name to label mapping */
  metricLabels: Record<string, string>;
  /** Whether any of the queries are loading */
  isLoading: boolean;
};

/**
 * Fetches metrics for all provided scopes in parallel and returns a combined
 * metric name to label lookup map.
 */
export const useGetAllScopeMetrics = ({ scopeNames }: UseGetAllScopeMetricsParams): UseGetAllScopeMetricsResult => {
  const metricsQueries = useQueries({
    queries: scopeNames.map(scopeName => ({
      queryKey: [API_ROUTES.GET_ALERT_METRICS.key, scopeName],
      queryFn: async () => {
        return makeRequest<GetAlertMetricsResponse>({
          url: `${API_BASE_URL}${API_ROUTES.GET_ALERT_METRICS.apiPath}?scope=${scopeName}`,
          init: { method: API_ROUTES.GET_ALERT_METRICS.method },
        });
      },
      enabled: !!scopeName,
      refetchOnWindowFocus: false,
      refetchInterval: false,
    })),
  });

  const isLoading = metricsQueries.some(query => query.isLoading);

  const metricLabels = useMemo(() => {
    const map: Record<string, string> = {};
    metricsQueries.forEach(query => {
      query.data?.data?.metrics?.forEach(m => {
        map[m.name] = m.label;
      });
    });
    return map;
  }, [metricsQueries]);

  return { metricLabels, isLoading };
};

