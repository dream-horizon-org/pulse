"use client";

import React, { type ReactNode, useMemo } from "react";
import { PulseProvider, PulseRouterEvents } from "@dreamhorizonorg/pulse-web/next";
import { PulseDataCollectionConsent } from "@dreamhorizonorg/pulse-web";
import type { InstrumentationConfig } from "@dreamhorizonorg/pulse-web";

/**
 * URL overrides for E2E / manual QA (see e2e/nextjs-demo.spec.ts @M4 network tests).
 * Read once per document load — full navigation is required for query changes to apply.
 */
function useDemoUrlPulseOptions(): {
  dataCollectionState: PulseDataCollectionConsent;
  instrumentations?: InstrumentationConfig;
} {
  return useMemo(() => {
    if (typeof window === "undefined") {
      return { dataCollectionState: PulseDataCollectionConsent.ALLOWED };
    }
    const q = new URLSearchParams(window.location.search);

    const consent = q.get("pulse_consent");
    const dataCollectionState =
      consent === "denied"
        ? PulseDataCollectionConsent.DENIED
        : PulseDataCollectionConsent.ALLOWED;

    const networkOff =
      q.get("pulse_network_enabled") === "0" ||
      q.get("pulse_network_enabled") === "false";
    const captureQueryParams = q.get("pulse_capture_query") === "1";
    const blockedUrlParam = q.get("pulse_blocked_url");
    const peerHost = q.get("pulse_peer_host");
    const peerService = q.get("pulse_peer_service");
    const propagateCors = q.get("pulse_propagate_cors");

    if (!networkOff && !captureQueryParams && !blockedUrlParam && !(peerHost && peerService) && !propagateCors) {
      return { dataCollectionState };
    }

    const instrumentations: InstrumentationConfig = {
      network: {
        enabled: !networkOff,
        ...(captureQueryParams ? { captureQueryParams: true } : {}),
        ...(blockedUrlParam ? { blockedUrls: [blockedUrlParam] } : {}),
        ...(peerHost && peerService ? { peerServiceMap: { [peerHost]: peerService } } : {}),
        ...(propagateCors ? { propagateTraceHeaderCorsUrls: [propagateCors] } : {}),
      },
    };
    return { dataCollectionState, instrumentations };
  }, []);
}

// E2E test hook: Playwright tests can set window.__TEST_PULSE_ERRORS_DISABLED = true
// via page.addInitScript() before page load to exercise the local kill-switch
// (instrumentations.errors.enabled: false) without changing app config.
type TestWindow = Window & { __TEST_PULSE_ERRORS_DISABLED?: boolean };
const testErrorsDisabled =
  typeof window !== "undefined" &&
  (window as TestWindow).__TEST_PULSE_ERRORS_DISABLED === true;

export function PulseClientProvider({
  children,
}: {
  children: ReactNode;
}): React.JSX.Element {
  const urlOpts = useDemoUrlPulseOptions();

  return (
    <PulseProvider
      config={{
        apiKey: process.env["NEXT_PUBLIC_PULSE_API_KEY"] ?? "demo-key",
        dataCollectionState: urlOpts.dataCollectionState,
        serviceName:
          process.env["NEXT_PUBLIC_PULSE_SERVICE_NAME"] ?? "nextjs-demo",
        ...(testErrorsDisabled && {
          instrumentations: { errors: { enabled: false } },
        }),
        ...(urlOpts.instrumentations !== undefined
          ? { instrumentations: urlOpts.instrumentations }
          : {}),
      }}
    >
      <PulseRouterEvents />
      {children}
    </PulseProvider>
  );
}
