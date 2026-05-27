/**
 * Minimal constants for pulse-web-rum. Do not import from `constants/Constants.ts`
 * here — that module pulls in screens and creates a circular dependency with RUM helpers.
 */

export const PULSE_RUM_COOKIE_KEYS = {
  USER_ID: "userId",
  USER_EMAIL: "userEmail",
  USER_NAME: "userName",
  TENANT_ID: "tenantId",
  TENANT_ROLE: "tenantRole",
  SYSTEM_ROLE: "systemRole",
} as const;
