/**
 * Human-readable labels for `pulse.type` (otel_logs `PulseType` / LogAttributes).
 *
 * Keep in sync with SDK sources:
 * - Android: `pulse-android-otel/pulse-semconv/.../PulseAttributes.kt` (`PulseTypeValues`)
 * - iOS: `pulse-ios-otel/Sources/PulseKit/PulseAttributes.swift` (`PulseTypeValues`)
 * - React Native: `pulse-react-native-otel/src/pulse.constants.ts` (`PULSE_TYPES`)
 * - RN native loggers use the same crash / non-fatal strings as each host SDK.
 *
 * Unknown values fall back to {@link formatPulseTypeFallback}.
 */

/** Exact `pulse.type` string → UI label (lowercase keys as emitted by SDKs). */
export const PULSE_TYPE_LABELS: Record<string, string> = {
  // Shared / multi-platform
  custom_event: "Custom event",
  non_fatal: "Non-fatal error",
  screen_load: "Screen load",
  screen_session: "Screen session",
  screen_interactive: "Screen interactive",
  app_start: "App start",
  "session.start": "Session start",
  "session.end": "Session end",
  "pulse.app.installation.start": "App installation",
  session_replay: "Session replay",
  interaction: "Interaction",
  "device.crash": "Crash",
  "device.anr": "ANR",
  // iOS-only ANR key (Android uses `device.anr`)
  anr: "ANR",
  // iOS jank (Android uses `app.jank.*`)
  frozen: "Frozen frame",
  slow: "Slow frame",
  "app.jank.frozen": "Frozen frame",
  "app.jank.slow": "Slow frame",
  // Tap / gesture (iOS uses both `touch` and `app.click`; Android maps touch to `app.click`)
  touch: "Touch",
  "app.click": "Tap",
  // Network (bare `network` is valid on iOS; status suffixed on all platforms)
  network: "Network",
  // Connectivity (naming differs by platform)
  network_change: "Network change",
  "network.change": "Network change",
  // Default when CH materializes missing `pulse.type`
  otel: "Log",
  // UI / query helpers (span-ish names sometimes appear in analytics)
  navigation: "Navigation",
  "screen.name": "Screen",
  // Edge / tests (e.g. malformed RN bridge); still nicer than raw slug
  "react-native": "React Native",
  // RN feature flags — rarely stored as `pulse.type`, mapped if present
  rn_screen_load: "Screen load (React Native)",
  rn_screen_session: "Screen session (React Native)",
  rn_screen_interactive: "Screen interactive (React Native)",
  rn_network: "Network (React Native)",
  custom_events: "Custom events",
  js_crash: "JavaScript crash",
};

const NETWORK_PREFIX = "network.";

function formatPulseTypeFallback(raw: string): string {
  return raw
    .split(/[._]/)
    .filter(Boolean)
    .map(
      (segment) =>
        segment.charAt(0).toUpperCase() + segment.slice(1).toLowerCase(),
    )
    .join(" ");
}

/**
 * Maps a raw `pulse.type` attribute value to a short UI label.
 * Handles dynamic `network.<status>` keys from HTTP instrumentation.
 */
export function getPulseTypeLabel(raw: string | null | undefined): string {
  const key = typeof raw === "string" ? raw.trim() : "";
  if (!key) {
    return "Log";
  }
  const mapped = PULSE_TYPE_LABELS[key];
  if (mapped) {
    return mapped;
  }
  if (key.startsWith(NETWORK_PREFIX)) {
    const rest = key.slice(NETWORK_PREFIX.length);
    if (/^\d+$/.test(rest)) {
      return `Network (HTTP ${rest})`;
    }
    return `Network (${rest})`;
  }
  return formatPulseTypeFallback(key);
}
