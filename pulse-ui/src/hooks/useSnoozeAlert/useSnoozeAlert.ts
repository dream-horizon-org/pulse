import { UseMutationResult, useMutation } from "@tanstack/react-query";
import { API_ROUTES, API_BASE_URL } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  SnoozeAlertInput,
  SnoozeAlertResponse,
} from "./useSnoozeAlert.interface";

type UseSnoozeAlertOptions = {
  onSettled?: (
    data: ApiResponse<SnoozeAlertResponse> | undefined,
    error: unknown,
    variables: SnoozeAlertInput,
    context: unknown,
  ) => void;
};

export const useSnoozeAlert = (
  options: UseSnoozeAlertOptions = {},
): UseMutationResult<
  ApiResponse<SnoozeAlertResponse>,
  unknown,
  SnoozeAlertInput,
  unknown
> => {
  const snoozeAlert = API_ROUTES.SNOOZE_ALERT;
  return useMutation<
    ApiResponse<SnoozeAlertResponse>,
    unknown,
    SnoozeAlertInput
  >({
    mutationFn: ({ alertId, snoozeAlertRequest }) => {
      return makeRequest({
        url: `${API_BASE_URL}${snoozeAlert.apiPath.replace("{id}", alertId)}`,
        init: {
          method: snoozeAlert.method,
          body: JSON.stringify(snoozeAlertRequest),
        },
      });
    },
    onSettled: options.onSettled,
  });
};


