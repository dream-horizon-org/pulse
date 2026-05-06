/**
 * Pages Router entry point — _app.tsx
 *
 * Mounts PulseProvider + useNextPagesRouterTracking for all Pages Router routes.
 * The PulseWeb singleton is shared with the App Router section (same browser
 * window), but navigating between the two sections triggers a full page reload,
 * so the SDK re-initialises on each section entry.
 */
import type { AppProps } from "next/app";
import React from "react";
import { PulseProvider } from "@dreamhorizon/pulse-web/next";
import { useNextPagesRouterTracking } from "@dreamhorizon/pulse-web/next";
import { PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";

/** Thin wrapper so the hook lives inside PulseProvider's React tree. */
function PulsePageTracker(): null {
  // skipInitial: false — capture the very first Pages Router navigation too
  useNextPagesRouterTracking({ skipInitial: false });
  return null;
}

export default function App({
  Component,
  pageProps,
}: AppProps): React.JSX.Element {
  return (
    <PulseProvider
      config={{
        apiKey: process.env["NEXT_PUBLIC_PULSE_API_KEY"] ?? "demo-key",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
        serviceName:
          process.env["NEXT_PUBLIC_PULSE_SERVICE_NAME"] ?? "nextjs-demo",
      }}
    >
      <PulsePageTracker />
      <Component {...pageProps} />
    </PulseProvider>
  );
}
