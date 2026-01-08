import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  TableMetadataResponse,
  TableMetadataErrorResponse,
} from "./useQueryMetadata.interface";

const QUERY_KEY = "QUERY_TABLE_METADATA";

/**
 * Hook to fetch table metadata (database name, table name, columns)
 */
export const useQueryMetadata = (enabled: boolean = true) => {
  return useQuery<
    ApiResponse<TableMetadataResponse>,
    TableMetadataErrorResponse
  >({
    queryKey: [QUERY_KEY],
    queryFn: () =>
      makeRequest<TableMetadataResponse>({
        url: `${API_BASE_URL}/query/metadata/table`,
        init: {
          method: "POST",
        },
      }),
    enabled,
    staleTime: 5 * 60 * 1000, // Cache for 5 minutes
    refetchOnWindowFocus: false,
  });
};

