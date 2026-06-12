import { useQuery, UseQueryOptions } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { getQueryParamString } from "../../helpers/queryParams";
import {
  RunUniversalQueryErrorResponse,
  RunUniversalQueryRequestBody,
  RunUniversalQuerySuccessResponseBody,
} from "./useQueryResultFromQueryId_diff.interface";

type useQueryResultFromQueryIdProps = Omit<
  UseQueryOptions<
    ApiResponse<RunUniversalQuerySuccessResponseBody>,
    RunUniversalQueryErrorResponse
  >,
  "queryFn" | "queryKey"
> &
  RunUniversalQueryRequestBody;

export const useGetQueryResultFromQueryId_diff = ({
  requestId,
  pageToken,
  enabled = true,
  ...queryOptions
}: useQueryResultFromQueryIdProps) => {
  return useQuery<
    ApiResponse<RunUniversalQuerySuccessResponseBody>,
    RunUniversalQueryErrorResponse
  >({
    queryKey: [API_ROUTES.GET_QUERY_RESULT_FROM_ID.key, requestId, pageToken],
    queryFn: async () => {
      const queryParams = getQueryParamString({ requestId, pageToken });
      if (!requestId) {
        return {
          data: null,
          error: {
            code: "",
            message: "Request ID is required",
            cause: "",
          },
          status: 400,
        } as ApiResponse<RunUniversalQuerySuccessResponseBody>;
      }

      return makeRequest<RunUniversalQuerySuccessResponseBody>({
        url: `${API_BASE_URL}${API_ROUTES.GET_QUERY_RESULT_FROM_ID.apiPath}${queryParams}`,
        init: {
          method: API_ROUTES.GET_QUERY_RESULT_FROM_ID.method,
        },
      });
    },
    enabled: false,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
    refetchOnReconnect: false,
    ...queryOptions,
  });
};
