import { UseMutationResult, useMutation } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { CreateAlertResponse, OnSettled } from "./useCreateAlert.interface";
import { AlertFormData } from "../../screens/AlertForm/AlertForm.interface";

export const useCreateAlert = (
  onSettled: OnSettled,
): UseMutationResult<
  ApiResponse<CreateAlertResponse>,
  unknown,
  AlertFormData,
  unknown
> => {
  const createAlert = API_ROUTES.CREATE_ALERT;
  return useMutation<ApiResponse<CreateAlertResponse>, unknown, AlertFormData>({
    mutationFn: (requestBody: AlertFormData) => {
      return makeRequest({
        url: `${API_BASE_URL}${createAlert.apiPath}`,
        init: {
          method: createAlert.method,
          body: JSON.stringify(requestBody),
        },
      });
    },
    onSettled: onSettled,
  });
};




