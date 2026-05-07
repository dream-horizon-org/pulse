"use client";

import React, { type ReactNode } from "react";
import { PulseProvider, PulseRouterEvents } from "@dreamhorizon/pulse-web/next";
import { PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";

export function PulseClientProvider({
  children,
}: {
  children: ReactNode;
}): React.JSX.Element {
  return (
    <PulseProvider
      config={{
        apiKey: process.env["NEXT_PUBLIC_PULSE_API_KEY"] ?? "demo-key",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
        serviceName:
          process.env["NEXT_PUBLIC_PULSE_SERVICE_NAME"] ?? "nextjs-demo",
      }}
    >
      <PulseRouterEvents />
      {children}
    </PulseProvider>
  );
}
