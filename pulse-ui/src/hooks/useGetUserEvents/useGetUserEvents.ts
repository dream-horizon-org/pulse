import { useMutation, UseMutationOptions } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  ErrorResponse,
  RequestBody,
  SuccessResponseBody,
} from "./useGetUserEvents.interface";

type GetUserEventsProps = Omit<
  UseMutationOptions<
    ApiResponse<SuccessResponseBody>,
    ErrorResponse,
    RequestBody
  >,
  "mutationFn"
> &
  RequestBody;
export const useGetUserEvents = ({
  fetchTime,
  phoneNo,
  onSuccess,
  onError,
  onSettled,
  onMutate,
}: GetUserEventsProps) => {
  return useMutation<
    ApiResponse<SuccessResponseBody>,
    ErrorResponse,
    RequestBody
  >({
    mutationKey: [API_ROUTES.GET_USER_EVENTS.key, phoneNo, fetchTime],
    onSuccess,
    mutationFn: (requestBody) => {
      return makeRequest<SuccessResponseBody>({
        url: `${API_BASE_URL}${API_ROUTES.GET_USER_EVENTS.apiPath}`,
        init: {
          method: API_ROUTES.GET_USER_EVENTS.method,
          body: JSON.stringify(requestBody),
        },
      });
    },
    onMutate,
    onError,
    onSettled,
  });
};
