import { useTenantContext } from '../contexts';
import { TIERS } from '../constants/Tiers';
import { TierLimits } from './useTierLimits.interface';

export function useTierLimits(): TierLimits {
  const { tier, projects } = useTenantContext();
  const currentProjectCount = projects.length;
  
  return {
    canCreateProjects: tier === TIERS.ENTERPRISE || currentProjectCount < 1,
    maxProjects: tier === TIERS.FREE ? 1 : null,
    currentProjectCount,
    isEnterprise: tier === TIERS.ENTERPRISE,
    isFree: tier === TIERS.FREE,
    tier,
  };
}
