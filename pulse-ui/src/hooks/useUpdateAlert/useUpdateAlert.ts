import { UseMutationResult, useMutation } from "@tanstack/react-query";
import { API_ROUTES, API_BASE_URL } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { AlertFormData } from "../../screens/AlertForm/AlertForm.interface";
import { CreateAlertResponse } from "../useCreateAlert/useCreateAlert.interface";
import { UpdateAlertOnSettled } from "./useUpdateAlert.interface";

export const useUpdateAlert = (
  onSettled: UpdateAlertOnSettled,
): UseMutationResult<
  ApiResponse<CreateAlertResponse>,
  unknown,
  AlertFormData,
  unknown
> => {
  const updateAlert = API_ROUTES.UPDATE_ALERT;
  return useMutation<ApiResponse<CreateAlertResponse>, unknown, AlertFormData>({
    mutationFn: (requestBody: AlertFormData) => {
      return makeRequest({
        url: `${API_BASE_URL}${updateAlert.apiPath}`,
        init: {
          method: updateAlert.method,
          body: JSON.stringify(requestBody),
        },
      });
    },
    onSettled: onSettled,
  });
};

