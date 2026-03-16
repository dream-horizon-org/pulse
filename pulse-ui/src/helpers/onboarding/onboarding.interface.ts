import {
  OnboardingRequest,
  OnboardingResponse,
} from "../../types/onboarding.types";

export interface OnboardingResult {
  data?: OnboardingResponse;
  error?: { message: string };
}

// Re-export shared types for convenience
export type { OnboardingRequest, OnboardingResponse };
