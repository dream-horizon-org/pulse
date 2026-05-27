export interface UsageLimitValueDto {
  displayName: string;
  windowType: string;
  dataType: string;
  value: number;
  overage: number;
  finalThreshold: number;
}

export interface TierRestResponse {
  tierId: number;
  name: string;
  displayName: string;
  isCustomLimitsAllowed: boolean;
  usageLimitDefaults: Record<string, UsageLimitValueDto>;
  isActive: boolean;
  createdAt: string;
}

export interface TierListEnvelope {
  tiers: TierRestResponse[];
}

export interface CreateTierPayload {
  name: string;
  displayName: string;
  isCustomLimitsAllowed: boolean;
  usageLimitDefaults: Record<string, { windowType: string; dataType: string; value: number; overage: number }>;
}

export interface UpdateTierPayload {
  displayName?: string;
  isCustomLimitsAllowed?: boolean;
  usageLimitDefaults?: Record<string, { windowType: string; dataType: string; value: number; overage: number }>;
}
