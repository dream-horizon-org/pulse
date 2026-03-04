import { useTenantContext } from '../contexts';

export interface TierLimits {
  canCreateProjects: boolean;
  maxProjects: number | null; // null = unlimited
  currentProjectCount: number;
  isEnterprise: boolean;
  isFree: boolean;
  tier: 'free' | 'enterprise' | null;
}

export function useTierLimits(): TierLimits {
  const { tier, projects } = useTenantContext();
  const currentProjectCount = projects.length;
  
  return {
    canCreateProjects: tier === 'enterprise' || currentProjectCount < 1,
    maxProjects: tier === 'free' ? 1 : null,
    currentProjectCount,
    isEnterprise: tier === 'enterprise',
    isFree: tier === 'free',
    tier,
  };
}
