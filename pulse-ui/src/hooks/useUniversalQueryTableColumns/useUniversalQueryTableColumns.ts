import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  UniversalQueryTableColumnsErrorResponse,
  UniversalQueryTableColumnsSuccessResponseBody,
} from "./useUniversalQueryTableColumns.interface";

export const useUniversalQueryTableColumns = (
  tableName: string,
  enabled: boolean,
) => {
  const apiCall = API_ROUTES.GET_UNIVERSAL_QUERY_COLUMNS;

  return useQuery<
    ApiResponse<UniversalQueryTableColumnsSuccessResponseBody>,
    UniversalQueryTableColumnsErrorResponse
  >({
    queryKey: [apiCall.key, tableName],
    queryFn: () =>
      makeRequest<UniversalQueryTableColumnsSuccessResponseBody>({
        url: `${API_BASE_URL}${apiCall.apiPath}?table=${tableName}`,
      }),
    enabled,
  });
};
