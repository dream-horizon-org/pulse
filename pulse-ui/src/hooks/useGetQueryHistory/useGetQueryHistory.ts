import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  GetQueryHistoryParams,
  GetQueryHistoryResponse,
} from "./useGetQueryHistory.interface";

/**
 * Hook to fetch query history for the current user
 * Uses GET /query/history
 */
export const useGetQueryHistory = ({
  enabled = true,
}: GetQueryHistoryParams = {}) => {
  const apiCall = API_ROUTES.GET_QUERY_HISTORY;

  return useQuery<ApiResponse<GetQueryHistoryResponse>>({
    queryKey: [apiCall.key],
    queryFn: async () => {
      return makeRequest<GetQueryHistoryResponse>({
        url: `${API_BASE_URL}${apiCall.apiPath}`,
        init: {
          method: apiCall.method,
        },
      });
    },
    enabled,
    refetchOnWindowFocus: false,
    staleTime: 30 * 1000, // Cache for 30 seconds
  });
};
