import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  RunUniversalQueryErrorResponse,
  RunUniversalQueryRequestBody,
  RunUniversalQuerySuccessResponseBody,
} from "./useQueryResultFromQueryId.interface";

type useRunUniversalQueryProps = Omit<
  UseMutationOptions<
    ApiResponse<RunUniversalQuerySuccessResponseBody>,
    RunUniversalQueryErrorResponse,
    RunUniversalQueryRequestBody
  >,
  "mutationFn" | "mutationKey"
> &
  RunUniversalQueryRequestBody;
export const useGetQueryResultFromQueryId = ({
  onSuccess,
  onError,
  requestId,
  pageToken,
}: useRunUniversalQueryProps) => {
  return useMutation<
    ApiResponse<RunUniversalQuerySuccessResponseBody>,
    RunUniversalQueryErrorResponse,
    RunUniversalQueryRequestBody
  >({
    mutationKey: [
      API_ROUTES.GET_QUERY_RESULT_FROM_QUERY_ID.key,
      requestId,
      pageToken,
    ],
    mutationFn: async (requestBody) =>
      makeRequest<RunUniversalQuerySuccessResponseBody>({
        url: `${API_BASE_URL}${API_ROUTES.GET_QUERY_RESULT_FROM_QUERY_ID.apiPath}`,
        init: {
          method: API_ROUTES.GET_QUERY_RESULT_FROM_QUERY_ID.method,
          body: JSON.stringify({ ...requestBody, requestId, pageToken }),
        },
      }),
    onSuccess,
    onError,
  });
};
