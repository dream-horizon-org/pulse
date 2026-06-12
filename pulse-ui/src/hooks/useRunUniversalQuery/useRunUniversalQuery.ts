import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  GetQueryIdErrorResponse,
  GetQueryIdRequestBody,
  GetQueryIdSuccessResponseBody,
} from "./useRunUniversalQuery.interface";

type useRunUniversalQueryProps = Omit<
  UseMutationOptions<
    ApiResponse<GetQueryIdSuccessResponseBody>,
    GetQueryIdErrorResponse,
    GetQueryIdRequestBody
  >,
  "mutationFn" | "mutationKey"
> &
  GetQueryIdRequestBody;
export const useRunUniversalQuery = ({
  onSuccess,
  onError,
  query,
  emailId,
}: useRunUniversalQueryProps) => {
  return useMutation<
    ApiResponse<GetQueryIdSuccessResponseBody>,
    GetQueryIdErrorResponse,
    GetQueryIdRequestBody
  >({
    mutationKey: [API_ROUTES.GET_QUERY_ID.key, query],
    mutationFn: async (requestBody) =>
      makeRequest<GetQueryIdSuccessResponseBody>({
        url: `${API_BASE_URL}${API_ROUTES.GET_QUERY_ID.apiPath}`,
        init: {
          method: API_ROUTES.GET_QUERY_ID.method,
          body: JSON.stringify({ ...requestBody, emailId }),
        },
      }),
    onSuccess,
    onError,
  });
};
