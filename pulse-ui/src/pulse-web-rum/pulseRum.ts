import { Pulse } from "@dreamhorizonorg/pulse-web";
import { getCookies } from "../helpers/cookies";
import {
  AnalyticsLabels,
  AnalyticsParams,
} from "../helpers/googleAnalytics/analyticsConstants";
import { isPulseRumEnabled } from "./pulseRumConfig";
import {
  withPulseEventContext,
  type PulseEventAttributes,
} from "./pulseEventContext";
import { PULSE_RUM_COOKIE_KEYS } from "./pulseRumConstants";
import type {
  PendingPulseEvent,
  PulseUserIdentity,
  TrackPulseEventArgs,
} from "./pulseRumTypes";

export type { PulseUserIdentity, TrackPulseEventArgs } from "./pulseRumTypes";

/** GA action / action:label → stable Pulse.trackEvent name (ids 600–610 seeds). */
const PULSE_EVENT_MAP: Record<string, string> = {
  "User logged in": "user_logged_in",
  "User logged in (dummy)": "user_logged_in",
  [`logout:${AnalyticsLabels.USER_LOGGED_OUT}`]: "user_logged_out",
};

const pendingEvents: PendingPulseEvent[] = [];
let eventsFlushInFlight: Promise<void> | null = null;

export function sanitizeAttributes(
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

function mapKey(action: string, label?: string): string {
  return label ? `${action}:${label}` : action;
}

export function resolvePulseEventName(
  action: string,
  label?: string,
  additionalParams?: Record<string, string | number | boolean>,
): string | null {
  const override = additionalParams?.[AnalyticsParams.PULSE_EVENT];
  if (typeof override === "string" && override.trim()) {
    return override.trim();
  }

  return (
    PULSE_EVENT_MAP[action] ??
    (label ? PULSE_EVENT_MAP[mapKey(action, label)] : undefined) ??
    null
  );
}

function trackReadyEvent(name: string, attrs?: PulseEventAttributes): void {
  const enriched = sanitizeAttributes(withPulseEventContext(attrs));
  Pulse.trackEvent(name, enriched);
}

function trackNamedEvent(name: string, attrs?: PulseEventAttributes): void {
  if (!isPulseRumEnabled()) return;

  if (!Pulse.isInitialized()) {
    pendingEvents.push({ name, attrs });
    void flushPendingPulseEvents();
    return;
  }

  trackReadyEvent(name, attrs);
}

export function flushPendingPulseEvents(): Promise<void> {
  if (!isPulseRumEnabled()) {
    return Promise.resolve();
  }
  if (eventsFlushInFlight) {
    return eventsFlushInFlight;
  }

  eventsFlushInFlight = (async () => {
    try {
      await Pulse.whenReady();
      if (!Pulse.isInitialized()) return;

      while (pendingEvents.length > 0) {
        const next = pendingEvents.shift();
        if (!next) continue;
        trackReadyEvent(next.name, next.attrs);
      }
    } finally {
      eventsFlushInFlight = null;
    }
  })();

  return eventsFlushInFlight;
}

export function trackPulseEvent({
  action,
  label,
  category,
  value,
  additionalParams,
}: TrackPulseEventArgs): void {
  const name = resolvePulseEventName(action, label, additionalParams);
  if (!name) return;

  const { [AnalyticsParams.PULSE_EVENT]: _pulseEvent, ...restParams } =
    additionalParams ?? {};

  const attrs: PulseEventAttributes = {
    ...restParams,
  };
  if (category !== undefined) attrs.category = category;
  if (label !== undefined) attrs.label = label;
  if (value !== undefined) attrs.value = value;

  trackNamedEvent(name, attrs);
}

/** Last identity passed before {@link Pulse.init} async bootstrap completes. */
let pendingIdentity: PulseUserIdentity | null = null;
let identityFlushInFlight: Promise<void> | null = null;

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
