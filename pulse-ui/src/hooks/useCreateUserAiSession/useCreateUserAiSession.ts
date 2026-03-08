import { useMutation, UseMutationResult } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  OnSettled,
  UseCreateUserAiSessionResponse,
} from "./useCreateUserAiSession.interface";
import { API_BASE_URL } from "../../constants";
import { API_ROUTES } from "../../constants";

export const useCreateUserAiSession = (
  onSettled: OnSettled,
): UseMutationResult<
  ApiResponse<UseCreateUserAiSessionResponse>,
  unknown,
  null,
  unknown
> => {
  const createUserAiSession = API_ROUTES.CREATE_USER_AI_SESSION;
  return useMutation<
    ApiResponse<UseCreateUserAiSessionResponse>,
    unknown,
    null
  >({
    mutationFn: () => {
      return makeRequest<UseCreateUserAiSessionResponse>({
        url: `${API_BASE_URL}${createUserAiSession.apiPath}`,
        init: {
          method: createUserAiSession.method,
        },
      });
    },
    onSettled: onSettled,
  });
};
