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

/** Navbar flat routes (must stay in sync with NAVBAR_ROUTES in Constants.ts). */
export const PULSE_NAV_ROUTES = {
  HOME: "/",
  USER_ENGAGEMENT: "/user-engagement",
  CRITICAL_INTERACTIONS: "/interactions",
  APP_VITALS: "/app-vitals",
  SCREENS: "/screens",
  NETWORK_LIST: "/network-apis",
  SESSION_REPLAY: "/session-replay/sessions",
  FUNNELS: "/funnels",
  JOURNEYS: "/journeys",
  ALERTS: "/alerts",
  AI_CHAT: "/ai-chat",
  EVENT_CATALOG: "/event-catalog",
} as const;
