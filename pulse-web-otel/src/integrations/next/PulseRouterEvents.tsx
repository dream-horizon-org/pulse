"use client";

import React, { Suspense, type JSX } from "react";
import { PulseIntegrationErrorBoundary } from "../react/pulse-integration-error-boundary";
import { useNextAppRouterTracking } from "./useNextAppRouterTracking";
import type {
  PulseRouterEventsProps,
  UseNextAppRouterTrackingOptions,
} from "../../types/next";

export type { PulseRouterEventsProps } from "../../types/next";

function NavigationEventsInner(
  props: Omit<PulseRouterEventsProps, "children">,
): null {
  useNextAppRouterTracking(props as UseNextAppRouterTrackingOptions);
  return null;
}

/**
 * Drop-in component for Next.js App Router screen tracking.
 *
 * Renders nothing — wraps `useNextAppRouterTracking` in a `<Suspense>`
 * boundary so `useSearchParams` does not force the entire page tree into
 * client-side rendering during SSR pre-rendering.
 *
 * Wraps tracking in an error boundary so render failures log via
 * `PulseWebLogger.alwaysError` without crashing the host app.
 *
 * Place it inside your root layout:
 *
 * ```tsx
 * // app/layout.tsx
 * import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/next";
 *
 * export default function RootLayout({ children }) {
 *   return (
 *     <html>
 *       <body>
 *         <PulseRouterEvents />
 *         {children}
 *       </body>
 *     </html>
 *   );
 * }
 * ```
 */
export function PulseRouterEvents({
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  children: _children,
  ...hookProps
}: PulseRouterEventsProps): JSX.Element {
  return (
    <PulseIntegrationErrorBoundary context="next-app">
      <Suspense fallback={null}>
        <NavigationEventsInner {...hookProps} />
      </Suspense>
    </PulseIntegrationErrorBoundary>
  );
}
