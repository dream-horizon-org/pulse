import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  AiQueryRequest,
  AiQueryResponse,
  AiQueryErrorResponse,
} from "./useAiQuery.interface";

type UseAiQueryOptions = Omit<
  UseMutationOptions<
    ApiResponse<AiQueryResponse>,
    AiQueryErrorResponse,
    AiQueryRequest
  >,
  "mutationFn" | "mutationKey"
>;

/**
 * Hook to submit a natural language query to the AI service.
 * The AI service converts the text to SQL, executes it, and returns results
 * in the same format as the regular query submission endpoint.
 */
export const useAiQuery = (options?: UseAiQueryOptions) => {
  return useMutation<
    ApiResponse<AiQueryResponse>,
    AiQueryErrorResponse,
    AiQueryRequest
  >({
    mutationKey: [API_ROUTES.AI_QUERY.key],
    mutationFn: async (request: AiQueryRequest) =>
      makeRequest<AiQueryResponse>({
        url: `${API_BASE_URL}${API_ROUTES.AI_QUERY.apiPath}`,
        init: {
          method: API_ROUTES.AI_QUERY.method,
          body: JSON.stringify(request),
          headers: {
            "Content-Type": "application/json",
          },
        },
      }),
    ...options,
  });
};

