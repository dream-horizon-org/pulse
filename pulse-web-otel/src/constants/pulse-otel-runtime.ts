/**
 * Central strings for OTel logger/scope names, {@link PulseInstrumentation} identifiers,
 * and browser DOM event types — single source to avoid typos in add/remove pairs.
 */

/** Names passed to {@code logs.getLogger} / {@code LoggerProvider.getLogger} (OTel scope). */
export const PulseOtelLoggerScope = {
  PULSE_WEB: "pulse-web",
  PULSE_WEB_SESSION: "pulse-web-session",
  PULSE_WEB_VITALS: "pulse-web-vitals",
  PULSE_WEB_NAVIGATION: "pulse-web-navigation",
  /** Android SdkInitializationEvents parity — {@code sdk.ts} init logs. */
  INITIALIZATION_EVENTS: "otel.initialization.events",
} as const;

/**
 * {@link PulseInstrumentation#name} — short id for registry / diagnostics (not the config key).
 * Config uses {@link InstrumentationKeys} (e.g. {@code webVitals}); this is the runtime label.
 */
export const PulseInstrumentationName = {
  SESSION: "session",
  WEB_VITALS: "web-vitals",
  INTERACTIONS: "interactions",
  NAVIGATION: "navigation",
} as const;

/** {@code addEventListener} / {@code removeEventListener} / {@code Event} type strings. */
export const DomEventType = {
  VISIBILITY_CHANGE: "visibilitychange",
  PAGESHOW: "pageshow",
  PAGEHIDE: "pagehide",
  BEFORE_UNLOAD: "beforeunload",
} as const;

/** Values of {@code document.visibilityState} for comparisons (same strings as the DOM API). */
export const DomVisibilityState = {
  HIDDEN: "hidden",
  VISIBLE: "visible",
} as const;
