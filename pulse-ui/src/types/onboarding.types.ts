import { TierType } from "../constants/Tiers";

/**
 * Shared types for onboarding flow.
 * These match the backend API contracts.
 */

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
  tenantRole: string;
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
