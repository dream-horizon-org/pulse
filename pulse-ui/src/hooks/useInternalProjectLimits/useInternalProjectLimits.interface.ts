import type { UsageLimitValueDto } from "../useInternalTiers/useInternalTiers.interface";
export type { UsageLimitValueDto };

export interface ProjectUsageLimitDto {
  projectUsageLimitId: number;
  projectId: string;
  usageLimits: Record<string, UsageLimitValueDto>;
  isActive: boolean;
  createdAt: string;
  createdBy: string;
  disabledAt?: string;
  disabledBy?: string;
}

export interface ProjectLimitHistoryDto {
  projectId: string;
  history: ProjectUsageLimitDto[];
  totalCount: number;
}

export interface SetLimitsPayload {
  limits: Record<string, { windowType: string; dataType: string; value: number; overage: number }>;
}

export interface ResetLimitsPayload {
  tierId?: number;
}
