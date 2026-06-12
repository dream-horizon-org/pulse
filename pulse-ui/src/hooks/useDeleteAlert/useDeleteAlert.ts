import { UseMutationResult, useMutation } from "@tanstack/react-query";
import { API_ROUTES, API_BASE_URL } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { DeleteAlertResponse, OnSettled } from "./useDeleteAlert.interface";

export const useAlertDelete = (
  onSettled: OnSettled,
  alertId: string | null,
): UseMutationResult<
  ApiResponse<DeleteAlertResponse>,
  unknown,
  null,
  unknown
> => {
  const deleteAlert = API_ROUTES.DELETE_ALERT;
  return useMutation<ApiResponse<DeleteAlertResponse>, unknown, null>({
    mutationFn: () => {
      if (!alertId) return {} as Promise<ApiResponse<DeleteAlertResponse>>;

      return makeRequest<DeleteAlertResponse>({
        url: `${API_BASE_URL}${deleteAlert.apiPath}/${alertId}`,
        init: {
          method: deleteAlert.method,
        },
      });
    },
    onSettled: onSettled,
  });
};
