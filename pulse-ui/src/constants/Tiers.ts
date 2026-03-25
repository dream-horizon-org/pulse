/**
 * Tier constants for tenant subscription levels
 * Used for feature gating and usage limits
 */
export const TIERS = {
  FREE: 'free',
  ENTERPRISE: 'enterprise',
} as const;

export type TierType = typeof TIERS[keyof typeof TIERS];
