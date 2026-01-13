import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  QueryStatsParams,
  QueryStatsResponse,
} from "./useQueryStats.interface";

/**
 * Format date to ISO string for API query params
 */
function formatDateParam(date: Date): string {
  return date.toISOString().split(".")[0]; // Remove milliseconds
}

/**
 * Hook to fetch query statistics for a date range
 * Uses GET /query/stats?startDate=...&endDate=...
 */
export const useQueryStats = ({
  startDate,
  endDate,
  enabled = true,
}: QueryStatsParams) => {
  const apiCall = API_ROUTES.GET_QUERY_STATS;

  const startDateStr = formatDateParam(startDate);
  const endDateStr = formatDateParam(endDate);

  return useQuery<ApiResponse<QueryStatsResponse>>({
    queryKey: [apiCall.key, startDateStr, endDateStr],
    queryFn: async () => {
      const params = new URLSearchParams({
        startDate: startDateStr,
        endDate: endDateStr,
      });
      
      return makeRequest<QueryStatsResponse>({
        url: `${API_BASE_URL}${apiCall.apiPath}?${params.toString()}`,
        init: {
          method: apiCall.method,
        },
      });
    },
    enabled,
    refetchOnWindowFocus: false,
    staleTime: 60 * 1000, // Cache for 1 minute
  });
};

