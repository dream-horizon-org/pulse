import { API_BASE_URL } from "../../constants";
import { makeRequest } from "../makeRequest";
import { OnboardingRequest, OnboardingResponse, OnboardingResult } from "./onboarding.interface";

export const completeOnboarding = async (
  request: OnboardingRequest,
  firebaseToken: string
): Promise<OnboardingResult> => {
  try {
    const response = await makeRequest<OnboardingResponse>({
      url: `${API_BASE_URL}/v1/onboarding/complete`,
      init: {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${firebaseToken}`,
        },
        body: JSON.stringify(request),
      },
    });
    
    if (response.data) {
      return { data: response.data };
    } else {
      return { 
        error: { 
          message: response.error?.message || 'Onboarding failed. Please try again.' 
        } 
      };
    }
  } catch (error: any) {
    return { 
      error: { 
        message: error.message || 'Onboarding failed. Please try again.' 
      } 
    };
  }
};
