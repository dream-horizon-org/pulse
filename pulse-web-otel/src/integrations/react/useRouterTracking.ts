"use client";

import { useEffect, useRef } from "react";
import { useLocation } from "react-router-dom";
import { Pulse } from "../../sdk";
import type { UseRouterTrackingOptions } from "../../types/react";

export type { UseRouterTrackingOptions } from "../../types/react";

/**
 * React Router v6 integration — calls {@link Pulse.setScreenName} on every
 * route change so subsequent signals (clicks, errors, web vitals, network)
 * carry the new `screen.name`.
 *
 * Mount this hook once, inside a component that renders underneath your
 * `<BrowserRouter>` / `<MemoryRouter>`:
 *
 * ```tsx
 * function AppRoutes() {
 *   useRouterTracking();
 *   return <Routes>...</Routes>;
 * }
 * ```
 *
 * **Peer dependency:** requires `react-router-dom >=6.0.0`.
 *
 * Implementation notes:
 * - No listeners are registered by this hook; it relies on React Router's
 *   `useLocation()` re-render to drive a `useEffect`. Nothing to clean up.
 * - Query-string and hash changes do **not** fire by default (pathname-only
 *   dependency). Pass `includeSearch: true` or a custom `format` to opt in.
 * - StrictMode-safe: the "skip initial" flag uses a ref, so React 18's double
 *   effect-invocation in dev does not cause a duplicate `setScreenName`.
 */
export function useRouterTracking(
  options: UseRouterTrackingOptions = {},
): void {
  const { format, includeSearch = false, skipInitial = true } = options;
  const location = useLocation();

  // Tracks the last dependency value we acted on. Initialised to `null` so
  // the first seen value is always "new". Persisting across fake StrictMode
  // unmount/remount means the second run sees the same dep and skips — that
  // is the desired StrictMode-safe behaviour.
  const prevDependency = useRef<string | null>(null);

  const dependency = includeSearch
    ? location.pathname + location.search
    : location.pathname;

  useEffect(() => {
    if (prevDependency.current === null) {
      // Very first run (or first run after a hard remount).
      prevDependency.current = dependency;
      if (skipInitial) return;
    } else if (prevDependency.current === dependency) {
      // StrictMode re-runs the effect with the same dep — treat as no-op.
      return;
    } else {
      prevDependency.current = dependency;
    }

    const name = format
      ? format({
          pathname: location.pathname,
          search: location.search,
          hash: location.hash,
        })
      : dependency;

    Pulse.setScreenName(name);
    Pulse.notifySoftNavigation();
    // `format`/`skipInitial` are stable across renders in practice; we key
    // solely on the derived location string so route-shape changes drive
    // the effect, not identity changes to the options object.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dependency]);
}
