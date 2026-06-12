"use client";

import type { JSX } from "react";
import type { PulseRouterEventsProps } from "../../types/react";
import { PulseIntegrationErrorBoundary } from "./pulse-integration-error-boundary";
import { useRouterTracking } from "./useRouterTracking";

export type { PulseRouterEventsProps } from "../../types/react";

function PulseRouterEventsInner(props: PulseRouterEventsProps): null {
  useRouterTracking(props);
  return null;
}

/**
 * React Router v6 — calls the SDK `setScreenName` on route changes.
 * Mount once inside a component under `<BrowserRouter>` / `<MemoryRouter>`,
 * typically next to your `<Routes>`.
 *
 * ```tsx
 * function AppRoutes() {
 *   return (
 *     <>
 *       <PulseRouterEvents />
 *       <Routes>...</Routes>
 *     </>
 *   );
 * }
 * ```
 *
 * **Peer dependency:** requires `react-router-dom >=6.0.0`.
 *
 * Renders a small error boundary so router misconfiguration (e.g. mounting
 * without a Router) logs via `PulseWebLogger.alwaysError` and does not
 * crash the host app.
 */
export function PulseRouterEvents(props: PulseRouterEventsProps): JSX.Element {
  return (
    <PulseIntegrationErrorBoundary context="react-router">
      <PulseRouterEventsInner {...props} />
    </PulseIntegrationErrorBoundary>
  );
}
