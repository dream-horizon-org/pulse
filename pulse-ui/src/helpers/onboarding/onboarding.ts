import { API_BASE_URL } from "../../constants";
import { makeRequest } from "../makeRequest";

export interface OnboardingRequest {
  organizationName: string;
  projectName: string;
  projectDescription?: string;
}

export interface OnboardingResponse {
  userId: string;
  email: string;
  name: string;
  tenantId: string;
  tenantName: string;
  tier: 'free' | 'enterprise';
  projectId: string;
  projectName: string;
  projectApiKey: string;
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  redirectTo?: string;
}

export const completeOnboarding = async (
  request: OnboardingRequest,
  firebaseToken: string
): Promise<{
  data?: OnboardingResponse;
  error?: { message: string };
}> => {
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
