"use client";

import React, { Suspense, type JSX } from "react";
import { useNextAppRouterTracking } from "./useNextAppRouterTracking";
import type {
  PulseNavigationEventsProps,
  UseNextAppRouterTrackingOptions,
} from "../../types/next";

export type { PulseNavigationEventsProps } from "../../types/next";

function NavigationEventsInner(
  props: Omit<PulseNavigationEventsProps, "children">,
): null {
  useNextAppRouterTracking(props as UseNextAppRouterTrackingOptions);
  return null;
}

/**
 * Drop-in component for Next.js App Router screen tracking.
 *
 * Renders nothing — wraps {@link useNextAppRouterTracking} in a `<Suspense>`
 * boundary so `useSearchParams` does not force the entire page tree into
 * client-side rendering during SSR pre-rendering.
 *
 * Place it inside your root layout:
 *
 * ```tsx
 * // app/layout.tsx
 * import { PulseNavigationEvents } from "@dreamhorizon/pulse-web/next";
 *
 * export default function RootLayout({ children }) {
 *   return (
 *     <html>
 *       <body>
 *         <PulseNavigationEvents />
 *         {children}
 *       </body>
 *     </html>
 *   );
 * }
 * ```
 */
export function PulseNavigationEvents({
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  children: _children,
  ...hookProps
}: PulseNavigationEventsProps): JSX.Element {
  return (
    <Suspense fallback={null}>
      <NavigationEventsInner {...hookProps} />
    </Suspense>
  );
}
