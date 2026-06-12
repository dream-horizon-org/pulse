import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  QueryValidationErrorResponse,
  QueryValidationRequestBody,
  QueryValidationSuccessResponseBody,
} from "./useValidateUniversalQuery.interface";

type ValidateUniversalQueryProps = Omit<
  UseMutationOptions<
    ApiResponse<QueryValidationSuccessResponseBody>,
    QueryValidationErrorResponse,
    QueryValidationRequestBody
  >,
  "mutationFn" | "mutationKey"
>;
export const useValidateUniversalQuery = ({
  onSuccess,
  onError,
}: ValidateUniversalQueryProps) => {
  return useMutation<
    ApiResponse<QueryValidationSuccessResponseBody>,
    QueryValidationErrorResponse,
    QueryValidationRequestBody
  >({
    mutationKey: [API_ROUTES.GET_VALIDATE_UNIVERSAL_QUERY.key],
    mutationFn: async (requestBody) =>
      makeRequest<QueryValidationSuccessResponseBody>({
        url: `${API_BASE_URL}${API_ROUTES.GET_VALIDATE_UNIVERSAL_QUERY.apiPath}`,
        init: {
          method: API_ROUTES.GET_VALIDATE_UNIVERSAL_QUERY.method,
          body: JSON.stringify(requestBody),
        },
      }),
    onSuccess,
    onError,
  });
};
