import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  TableMetadataResponse,
  TableMetadataErrorResponse,
} from "./useQueryMetadata.interface";


/**
 * Hook to fetch table metadata (database name, table name, columns)
 */
export const useQueryMetadata = (enabled: boolean = true) => {
  const apiCall = API_ROUTES.GET_QUERY_TABLES;

  return useQuery<
    ApiResponse<TableMetadataResponse>,
    TableMetadataErrorResponse
  >({
    queryKey: [apiCall.key],
    queryFn: () =>
      makeRequest<TableMetadataResponse>({
        url: `${API_BASE_URL}${apiCall.apiPath}`,
        init: {
          method: apiCall.method,
        },
      }),
    enabled,
    staleTime: 60 * 1000, // Cache for 1 minute
    refetchOnWindowFocus: false,
  });
};

