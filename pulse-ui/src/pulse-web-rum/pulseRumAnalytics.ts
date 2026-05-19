import { Pulse } from "@dreamhorizonorg/pulse-web";
import { getCookies } from "../helpers/cookies";
import { isPulseRumEnabled } from "./pulseRumConfig";
import {
  withPulseEventContext,
  type PulseEventAttributes,
} from "./pulseEventContext";
import { PULSE_NAV_ROUTES, PULSE_RUM_COOKIE_KEYS } from "./pulseRumConstants";

export type { PulseEventAttributes } from "./pulseEventContext";

/** Stable slugs for interaction step configs (nav click → screen loaded). */
const NAV_ROUTE_DESTINATIONS: Record<string, string> = {
  [PULSE_NAV_ROUTES.HOME]: "home",
  [PULSE_NAV_ROUTES.USER_ENGAGEMENT]: "user_engagement",
  [PULSE_NAV_ROUTES.CRITICAL_INTERACTIONS]: "interactions",
  [PULSE_NAV_ROUTES.APP_VITALS]: "app_vitals",
  [PULSE_NAV_ROUTES.SCREENS]: "screens",
  [PULSE_NAV_ROUTES.NETWORK_LIST]: "network_apis",
  [PULSE_NAV_ROUTES.SESSION_REPLAY]: "session_replay",
  [PULSE_NAV_ROUTES.FUNNELS]: "funnels",
  [PULSE_NAV_ROUTES.JOURNEYS]: "journeys",
  [PULSE_NAV_ROUTES.ALERTS]: "alerts",
  [PULSE_NAV_ROUTES.AI_CHAT]: "ai_chat",
  [PULSE_NAV_ROUTES.EVENT_CATALOG]: "event_catalog",
};

export type PulseUserIdentity = {
  userId: string;
  email?: string;
  name?: string;
  tenantId?: string;
  tenantRole?: string;
  systemRole?: string;
};

/** Last identity passed before {@link Pulse.init} async bootstrap completes. */
let pendingIdentity: PulseUserIdentity | null = null;
let identityFlushInFlight: Promise<void> | null = null;

function sanitizeAttributes(
  attrs?: PulseEventAttributes,
): Record<string, string | number | boolean> | undefined {
  if (!attrs) return undefined;
  const out: Record<string, string | number | boolean> = {};
  for (const [key, value] of Object.entries(attrs)) {
    if (value === null || value === undefined) continue;
    out[key] = value;
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

function applyPulseUserIdentity(identity: PulseUserIdentity): void {
  Pulse.setUserId(identity.userId);
  const properties: Record<string, string | null> = {
    email: identity.email ?? null,
    name: identity.name ?? null,
    tenant_id: identity.tenantId ?? null,
    tenant_role: identity.tenantRole ?? null,
    system_role: identity.systemRole ?? null,
  };
  Pulse.setUserProperties(properties);
}

function readPulseUserIdentityFromCookies(): PulseUserIdentity | null {
  const userId = getCookies(PULSE_RUM_COOKIE_KEYS.USER_ID);
  if (!userId || userId === "undefined") return null;

  return {
    userId,
    email: getCookies(PULSE_RUM_COOKIE_KEYS.USER_EMAIL) || undefined,
    name: getCookies(PULSE_RUM_COOKIE_KEYS.USER_NAME) || undefined,
    tenantId: getCookies(PULSE_RUM_COOKIE_KEYS.TENANT_ID) || undefined,
    tenantRole: getCookies(PULSE_RUM_COOKIE_KEYS.TENANT_ROLE) || undefined,
    systemRole: getCookies(PULSE_RUM_COOKIE_KEYS.SYSTEM_ROLE) || undefined,
  };
}

/**
 * Applies {@link pendingIdentity} (or cookies) after {@link Pulse.whenReady}.
 * Safe to call from login handlers before the SDK finishes async init.
 */
export function flushPulseUserIdentityWhenReady(): Promise<void> {
  if (!isPulseRumEnabled()) {
    return Promise.resolve();
  }
  if (identityFlushInFlight) {
    return identityFlushInFlight;
  }

  identityFlushInFlight = (async () => {
    try {
      await Pulse.whenReady();
      if (!Pulse.isInitialized()) return;

      const identity = pendingIdentity ?? readPulseUserIdentityFromCookies();
      if (!identity?.userId?.trim()) return;

      applyPulseUserIdentity(identity);
      pendingIdentity = null;
    } finally {
      identityFlushInFlight = null;
    }
  })();

  return identityFlushInFlight;
}

/** No-op when RUM is disabled or Pulse has not finished init. */
export function trackPulseEvent(
  name: string,
  attrs?: PulseEventAttributes,
): void {
  if (!isPulseRumEnabled() || !Pulse.isInitialized()) return;
  Pulse.trackEvent(name, sanitizeAttributes(withPulseEventContext(attrs)));
}

/** Navbar journey step — pair with a matching `*_loaded` event on the destination screen. */
export function trackNavItemClicked(routeTo: string, navLabel?: string): void {
  trackPulseEvent("nav_item_clicked", {
    destination: NAV_ROUTE_DESTINATIONS[routeTo] ?? routeTo,
    nav_label: navLabel,
  });
}

export function syncPulseUserIdentity(identity: PulseUserIdentity): void {
  if (!isPulseRumEnabled()) return;
  if (!identity.userId?.trim()) return;

  if (!Pulse.isInitialized()) {
    pendingIdentity = identity;
    void flushPulseUserIdentityWhenReady();
    return;
  }

  pendingIdentity = null;
  applyPulseUserIdentity(identity);
}

export function syncPulseUserIdentityFromCookies(): void {
  const identity = readPulseUserIdentityFromCookies();
  if (!identity) return;
  syncPulseUserIdentity(identity);
}

export function clearPulseUserIdentity(): void {
  pendingIdentity = null;
  if (!isPulseRumEnabled()) return;
  if (Pulse.isInitialized()) {
    Pulse.clearUserIdentity();
  }
}
