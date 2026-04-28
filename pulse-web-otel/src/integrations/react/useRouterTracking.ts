"use client";

import { useEffect, useRef } from "react";
import { PulseWeb } from "../../sdk";
import type { UseRouterTrackingOptions } from "../../types/react";

export type { UseRouterTrackingOptions } from "../../types/react";

type RouterLocation = {
  pathname: string;
  search: string;
  hash: string;
};

type UseLocationHook = () => RouterLocation;

/**
 * React Router v6 integration — calls {@link PulseWeb.setScreenName} on every
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
 * **Peer dependency:** requires `react-router-dom >=6.0.0` (declared as an
 * optional peer dep in `@dreamhorizon/pulse-web`). Install it alongside this
 * package if you use this hook. If `react-router-dom` is absent the hook
 * throws a clear `[PulseWeb]` error at call-time (not at import-time) so the
 * rest of `@dreamhorizon/pulse-web/react` (`PulseProvider`, `PulseErrorBoundary`,
 * `usePulse`) continues to work without a router installed.
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

  // Lazy require so the module loads without react-router-dom installed.
  // Throws at call-time (not import-time) if the peer dep is absent.
  let useLocationHook: UseLocationHook;
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    useLocationHook = (
      require("react-router-dom") as { useLocation: UseLocationHook }
    ).useLocation;
  } catch {
    throw new Error(
      "[PulseWeb] useRouterTracking requires react-router-dom >=6.0.0. " +
        "Install it as a peer dependency or remove this hook.",
    );
  }

  // eslint-disable-next-line react-hooks/rules-of-hooks
  let location: RouterLocation;
  try {
    location = useLocationHook();
  } catch {
    throw new Error(
      "[PulseWeb] routerTracking requires <PulseProvider> to be rendered inside " +
        "a <BrowserRouter> or equivalent React Router v6 context. " +
        "Wrap your app root with <BrowserRouter> or remove the routerTracking prop.",
    );
  }
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

    PulseWeb.setScreenName(name);
    // `format`/`skipInitial` are stable across renders in practice; we key
    // solely on the derived location string so route-shape changes drive
    // the effect, not identity changes to the options object.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dependency]);
}
