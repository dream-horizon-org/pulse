import { UseMutationResult, useMutation } from "@tanstack/react-query";
import { API_ROUTES, API_BASE_URL } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { ResumeAlertResponse } from "./useResumeAlert.interface";

type UseResumeAlertOptions = {
  onSettled?: (
    data: ApiResponse<ResumeAlertResponse> | undefined,
    error: unknown,
    variables: string,
    context: unknown,
  ) => void;
};

export const useResumeAlert = (
  options: UseResumeAlertOptions = {},
): UseMutationResult<
  ApiResponse<ResumeAlertResponse>,
  unknown,
  string,
  unknown
> => {
  const resumeAlert = API_ROUTES.RESUME_ALERT;
  return useMutation<ApiResponse<ResumeAlertResponse>, unknown, string>({
    mutationFn: (alertId: string) => {
      return makeRequest({
        url: `${API_BASE_URL}${resumeAlert.apiPath.replace("{id}", alertId)}`,
        init: {
          method: resumeAlert.method,
        },
      });
    },
    onSettled: options.onSettled,
  });
};




