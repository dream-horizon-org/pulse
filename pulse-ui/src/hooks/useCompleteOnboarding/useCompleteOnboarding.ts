import { useMutation } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { CompleteOnboardingParams, OnboardingResponse } from "./useCompleteOnboarding.interface";

export const useCompleteOnboarding = () => {
  const route = API_ROUTES.COMPLETE_ONBOARDING;

  return useMutation<ApiResponse<OnboardingResponse>, unknown, CompleteOnboardingParams>({
    mutationFn: async ({ request, firebaseToken }: CompleteOnboardingParams) => {
      const response = await makeRequest<OnboardingResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${firebaseToken}`,
          },
          body: JSON.stringify(request),
        },
      });

      if (response.error || !response.data) {
        throw new Error(response.error?.message ?? 'Onboarding failed. Please try again.');
      }

      return response;
    },
  });
};
