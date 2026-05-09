import React, { useEffect } from "react";
import { useLocation } from "react-router-dom";
import { Pulse } from "@dreamhorizonorg/pulse-web";

/**
 * ScreenNavigationLogger — monitors and logs screen navigation signals.
 *
 * When navigating, this component logs to the browser console:
 *   - screen_load: page load / SPA nav (Navigation Timing incl. tti on initial load)
 *   - screen_session: time spent on previous screen (SDK emits on route change / unload)
 *
 * Developers can open DevTools (F12) to see signals being emitted.
 */
export function ScreenNavigationLogger(): null {
  const location = useLocation();

  useEffect(() => {
    const logScreenSignal = (type: string, attrs: Record<string, unknown>) => {
      if (!import.meta.env.DEV) return;
      console.log(`📍 [screen_${type}]`, {
        type: `screen_${type}`,
        ...attrs,
        timestamp: new Date().toISOString(),
      });
    };

    // Log the navigation
    logScreenSignal("load", {
      screen: location.pathname,
      reason: "SPA navigation detected",
    });

    // Pulse SDK will emit screen_load, screen_session, etc. asynchronously.
    // We can't directly hook them without access to the telemetry pipeline,
    // but we log the route change here for visibility.
  }, [location.pathname]);

  // In dev mode, expose Pulse to window for manual signal inspection
  useEffect(() => {
    if (!import.meta.env.DEV) return;
    const w = window as unknown as Record<string, unknown>;
    if (!w.Pulse) {
      w.Pulse = Pulse;
    }
    console.log(
      "%c📊 Screen Navigation Signals %cLive in DevTools Console",
      "color:#4f46e5;font-weight:bold;font-size:12px",
      "color:#64748b;font-size:11px",
    );
    console.log(
      "%cOpen DevTools (F12) → Network tab to see OTLP exports %c/v1/traces",
      "color:#64748b;font-size:11px",
      "color:#a78bfa;font-weight:bold",
    );
    console.log(
      "%cOr toggle Pulse Debug with Shift+P to monitor exports",
      "color:#64748b;font-size:11px",
    );
  }, []);

  return null;
}
