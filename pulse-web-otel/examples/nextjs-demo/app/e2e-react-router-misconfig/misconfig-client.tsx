"use client";

import type { ReactElement } from "react";
import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";

/**
 * Intentionally mounts the **React Router** Pulse adapter without a
 * `<BrowserRouter>`. The SDK must log via PulseWebLogger and must not
 * white-screen the Next.js host (regression test for PulseRouterEvents).
 */
export default function MisconfigClient(): ReactElement {
  return (
    <main data-testid="e2e-react-router-misconfig-root">
      <h1>React-router adapter probe</h1>
      <p>
        Import <code>@dreamhorizonorg/pulse-web/react/router</code>,{" "}
        <code>PulseRouterEvents</code> without <code>BrowserRouter</code> — the
        page must stay visible.
      </p>
      <PulseRouterEvents skipInitial={false} />
    </main>
  );
}
