"use client";

import { PulseProvider as SDKPulseProvider } from "@dreamhorizon/pulse-web/react";
import { PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";

// 'use client' boundary — SDK dist does not ship the directive so it cannot
// be imported directly from a Next.js Server Component.
export function PulseProvider({ children }: { children: React.ReactNode }) {
  const config = {
    apiKey: process.env.NEXT_PUBLIC_PULSE_API_KEY ?? "default-project_devkey01",
    serviceName: "lottery-demo",
    serviceVersion: process.env.NEXT_PUBLIC_APP_VERSION ?? "1.0.0",
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    instrumentations: {
      errors:     { enabled: true },
      network:    { enabled: true },
      clicks:     { enabled: true },
      webVitals:  { enabled: true },
      navigation: { enabled: true },
      session:    { enabled: true },
    },
  };

  return (
    <SDKPulseProvider config={config} shutdownOnUnmount={false}>
      {children}
    </SDKPulseProvider>
  );
}
