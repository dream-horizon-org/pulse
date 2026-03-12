import { TierType } from "../../constants/Tiers";

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
}

export interface LoginResult {
  data?: LoginResponse;
  error?: { message: string };
}
