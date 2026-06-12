import { useQuery } from "@tanstack/react-query";
import {
  UniversalQueryTablesErrorResponse,
  UniversalQueryTablesSuccessResponseBody,
} from ".";
import { API_ROUTES, API_BASE_URL } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";

export const useUniversalQueryTables = () => {
  const apiCall = API_ROUTES.GET_UNIVERSAL_QUERY_TABLES;

  return useQuery<
    ApiResponse<UniversalQueryTablesSuccessResponseBody>,
    UniversalQueryTablesErrorResponse
  >({
    queryKey: [apiCall.key],
    queryFn: async () =>
      makeRequest<UniversalQueryTablesSuccessResponseBody>({
        url: `${API_BASE_URL}${apiCall.apiPath}`,
        init: {
          method: apiCall.method,
        },
      }),
  });
};
