import { TierType } from "../../constants/Tiers";
import { SYSTEM_ROLES } from "../../constants";

export interface LoginRequest {
  firebaseIdToken: string;
}

export interface LoginResponse {
  status: string;
  accessToken?: string;
  refreshToken?: string;
  userId: string;
  email: string;
  name: string;
  tenantId?: string;
  tenantName?: string;
  tenantRole?: string;
  tier?: TierType;
  needsOnboarding: boolean;
  tokenType?: string;
  expiresIn?: number;
  systemRole?: typeof SYSTEM_ROLES[keyof typeof SYSTEM_ROLES];
  redirectTo?: string;
}

export interface LoginResult {
  data?: LoginResponse;
  error?: { message: string };
}
