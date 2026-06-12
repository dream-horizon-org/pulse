import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import { API_BASE_URL } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  SubmitQueryRequest,
  SubmitQueryResponse,
  QueryErrorResponse,
} from "./useSubmitQuery.interface";

type UseSubmitQueryOptions = Omit<
  UseMutationOptions<
    ApiResponse<SubmitQueryResponse>,
    QueryErrorResponse,
    SubmitQueryRequest
  >,
  "mutationFn" | "mutationKey"
>;

/**
 * Hook to submit a SQL query for execution
 */
export const useSubmitQuery = (options?: UseSubmitQueryOptions) => {
  return useMutation<
    ApiResponse<SubmitQueryResponse>,
    QueryErrorResponse,
    SubmitQueryRequest
  >({
    mutationKey: ["SUBMIT_QUERY"],
    mutationFn: async (request: SubmitQueryRequest) =>
      makeRequest<SubmitQueryResponse>({
        url: `${API_BASE_URL}/query`,
        init: {
          method: "POST",
          body: JSON.stringify(request),
          headers: {
            "Content-Type": "application/json",
          },
        },
      }),
    ...options,
  });
};

