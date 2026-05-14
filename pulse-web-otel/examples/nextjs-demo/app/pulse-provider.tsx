"use client";

import React, { type ReactNode } from "react";
import { PulseProvider, PulseRouterEvents } from "@dreamhorizonorg/pulse-web/next";
import { PulseDataCollectionConsent } from "@dreamhorizonorg/pulse-web";

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
  return (
    <PulseProvider
      config={{
        apiKey: process.env["NEXT_PUBLIC_PULSE_API_KEY"] ?? "demo-key",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
        serviceName:
          process.env["NEXT_PUBLIC_PULSE_SERVICE_NAME"] ?? "nextjs-demo",
        ...(testErrorsDisabled && {
          instrumentations: { errors: { enabled: false } },
        }),
      }}
    >
      <PulseRouterEvents />
      {children}
    </PulseProvider>
  );
}
