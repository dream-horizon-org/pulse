"use client";

import {
  PulseProvider as SDKPulseProvider,
  PulseRouterEvents,
} from "@dreamhorizon/pulse-web/next";
import { PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";

// 'use client' boundary — SDK dist does not ship the directive so it cannot
// be imported directly from a Next.js Server Component.
export function PulseProvider({ children }: { children: React.ReactNode }) {
  const apiKey = process.env.NEXT_PUBLIC_PULSE_API_KEY;
  if (!apiKey) {
    throw new Error(
      "Missing NEXT_PUBLIC_PULSE_API_KEY for lottery-demo Pulse integration",
    );
  }

  const config = {
    apiKey,
    serviceName: "lottery-demo",
    serviceVersion: process.env.NEXT_PUBLIC_APP_VERSION ?? "1.0.0",
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    instrumentations: {
      errors: { enabled: true },
      network: { enabled: true },
      clicks: { enabled: true },
      webVitals: { enabled: true },
      navigation: { enabled: true },
      session: { enabled: true },
    },
  };

  return (
    <SDKPulseProvider config={config} shutdownOnUnmount={false}>
      <PulseRouterEvents />
      {children}
    </SDKPulseProvider>
  );
}
