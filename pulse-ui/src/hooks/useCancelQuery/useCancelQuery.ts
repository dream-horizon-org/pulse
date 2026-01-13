import { UseMutationOptions, useMutation } from "@tanstack/react-query";
import { API_ROUTES, API_BASE_URL } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  CancelQueryRequestType,
  CancelQueryResponseType,
} from "./useCancelQuery.interface";

type CancelQueryErrorResponse = {
  error: {
    message: string;
    cause?: string;
  };
  data: null;
  status: number;
};

type UseCancelQueryOptions = Omit<
  UseMutationOptions<
    ApiResponse<CancelQueryResponseType>,
    CancelQueryErrorResponse,
    CancelQueryRequestType
  >,
  "mutationFn" | "mutationKey"
>;

/**
 * Hook to cancel a running query by job ID
 * Uses DELETE /query/job/{jobId}
 */
export const useCancelQuery = (options?: UseCancelQueryOptions) => {
  return useMutation<
    ApiResponse<CancelQueryResponseType>,
    CancelQueryErrorResponse,
    CancelQueryRequestType
  >({
    mutationKey: [API_ROUTES.CANCEL_QUERY.key],
    mutationFn: async ({ jobId }) =>
      makeRequest<CancelQueryResponseType>({
        url: `${API_BASE_URL}${API_ROUTES.CANCEL_QUERY.apiPath}/${jobId}`,
        init: {
          method: API_ROUTES.CANCEL_QUERY.method,
        },
      }),
    ...options,
  });
};
