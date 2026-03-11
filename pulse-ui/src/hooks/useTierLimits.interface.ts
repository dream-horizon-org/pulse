import { TierType } from '../constants/Tiers';

export interface TierLimits {
  canCreateProjects: boolean;
  maxProjects: number | null; // null = unlimited
  currentProjectCount: number;
  isEnterprise: boolean;
  isFree: boolean;
  tier: TierType | null;
}
