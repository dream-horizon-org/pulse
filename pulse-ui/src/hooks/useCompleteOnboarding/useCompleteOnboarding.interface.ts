import { OnboardingRequest } from "../../types/onboarding.types";

export interface CompleteOnboardingParams {
  request: OnboardingRequest;
  firebaseToken: string;
}
