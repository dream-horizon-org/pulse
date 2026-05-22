import { PulseWebLogger } from "../../pulse-web-logger";
import { Pulse } from "../../sdk";
import type { PulseLocationLike } from "../../types/react";

export const PULSE_ROUTER_LOG_PREFIX = "[pulse:router]";

/**
 * Runs user {@link format} safely. Returns null if the function throws — caller should skip navigation apply.
 */
export function resolvePulseScreenName(
  format: ((loc: PulseLocationLike) => string) | undefined,
  dependency: string,
  location: PulseLocationLike,
): string | null {
  if (!format) {
    return dependency;
  }
  try {
    return format(location);
  } catch (e) {
    PulseWebLogger.alwaysError(
      `${PULSE_ROUTER_LOG_PREFIX} format() threw; screen.name not updated for this navigation.`,
      e,
    );
    return null;
  }
}

/**
 * Updates {@link Pulse.setScreenName} and {@link Pulse.notifySoftNavigation} without surfacing errors to the host app.
 */
export function applyPulseScreenNavigation(
  screenName: string,
  detail: string,
): void {
  try {
    Pulse.setScreenName(screenName);
    Pulse.notifySoftNavigation();
  } catch (e) {
    PulseWebLogger.alwaysError(
      `${PULSE_ROUTER_LOG_PREFIX} ${detail} — screen.navigation skipped.`,
      e,
    );
  }
}
