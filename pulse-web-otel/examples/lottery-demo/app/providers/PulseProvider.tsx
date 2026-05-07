"use client";

import {
  PulseProvider as SDKPulseProvider,
  PulseRouterEvents,
} from "@dreamhorizon/pulse-web/next";
import { PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";

// 'use client' boundary — SDK dist does not ship the directive so it cannot
// be imported directly from a Next.js Server Component.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function PulseProvider({ children }: { children: any }) {
  const config = {
    apiKey: process.env.NEXT_PUBLIC_PULSE_API_KEY ?? "default-project-lottery-gGqK4tv6_91X8VQVXx8ubqUJAZTZUhsfs",
    serviceName: "lottery-demo",
    serviceVersion: process.env.NEXT_PUBLIC_APP_VERSION ?? "1.0.0",
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    instrumentations: {
      errors:     { enabled: false },
      network:    { enabled: false },
      clicks:     { enabled: false },
      webVitals:  { enabled: false },
      navigation: { enabled: false },
      session:    { enabled: false },
    },
  };

  return (
    <SDKPulseProvider config={config} shutdownOnUnmount={false}>
      <PulseRouterEvents />
      {children}
    </SDKPulseProvider>
  );
}
