/**
 * Pages Router entry point — _app.tsx
 *
 * Mounts PulseProvider + useNextPagesRouterTracking for all Pages Router routes.
 * The `Pulse` SDK singleton is shared with the App Router section (same browser
 * window), but navigating between the two sections triggers a full page reload,
 * so the SDK re-initialises on each section entry.
 */
import type { AppProps } from "next/app";
import React, { useMemo } from "react";
import { PulseProvider } from "@dreamhorizonorg/pulse-web/next";
import { useNextPagesRouterTracking } from "@dreamhorizonorg/pulse-web/next";
import { PulseDataCollectionConsent, PulseLogLevel } from "@dreamhorizonorg/pulse-web";
import type { InstrumentationConfig } from "@dreamhorizonorg/pulse-web";

/** Thin wrapper so the hook lives inside PulseProvider's React tree. */
function PulsePageTracker(): null {
  // skipInitial: false — capture the very first Pages Router navigation too
  useNextPagesRouterTracking({ skipInitial: false });
  return null;
}

/**
 * URL overrides for E2E / manual QA — mirrors pulse-provider.tsx for App Router.
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

export default function App({
  Component,
  pageProps,
}: AppProps): React.JSX.Element {
  const urlOpts = useDemoUrlPulseOptions();

  return (
    <PulseProvider
      config={{
        apiKey: process.env["NEXT_PUBLIC_PULSE_API_KEY"] ?? "demo-key",
        dataCollectionState: urlOpts.dataCollectionState,
        serviceName:
          process.env["NEXT_PUBLIC_PULSE_SERVICE_NAME"] ?? "nextjs-demo",
        logLevel: PulseLogLevel.DEBUG,
        ...(urlOpts.instrumentations !== undefined
          ? { instrumentations: urlOpts.instrumentations }
          : {}),
      }}
    >
      <PulsePageTracker />
      <Component {...pageProps} />
    </PulseProvider>
  );
}
