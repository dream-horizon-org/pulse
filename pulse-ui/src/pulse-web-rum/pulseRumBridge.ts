import { Pulse } from "@dreamhorizonorg/pulse-web";
import {
  AnalyticsLabels,
  AnalyticsParams,
} from "../helpers/googleAnalytics/analyticsConstants";
import { isPulseRumEnabled } from "./pulseRumConfig";
import {
  withPulseEventContext,
  type PulseEventAttributes,
} from "./pulseEventContext";

/** GA action / action:label → stable Pulse.trackEvent name (ids 600–610 seeds). */
const PULSE_EVENT_MAP: Record<string, string> = {
  "User logged in": "user_logged_in",
  "User logged in (dummy)": "user_logged_in",
  [`logout:${AnalyticsLabels.USER_LOGGED_OUT}`]: "user_logged_out",
};

type PendingPulseEvent = {
  name: string;
  attrs?: PulseEventAttributes;
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

export function forwardPulseCustomEvent(
  name: string,
  attrs?: PulseEventAttributes,
): void {
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

export type LogEventPulseForwardArgs = {
  action: string;
  label?: string;
  category?: string;
  value?: number;
  additionalParams?: Record<string, string | number | boolean>;
};

export function forwardPulseEventFromLogEvent({
  action,
  label,
  category,
  value,
  additionalParams,
}: LogEventPulseForwardArgs): void {
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

  forwardPulseCustomEvent(name, attrs);
}
