import { TierType } from "../../constants/Tiers";

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
  tier: TierType;
  projectId: string;
  projectName: string;
  projectApiKey: string;
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  redirectTo?: string;
}

export interface CompleteOnboardingParams {
  request: OnboardingRequest;
  firebaseToken: string;
}
