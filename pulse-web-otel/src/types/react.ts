import type { ReactNode } from "react";
import type { PulseWebConfig } from "../config";

export interface PulseErrorBoundaryProps {
  children: ReactNode;
  /**
   * Optional UI when a child throws during render. Must not rely on React context
   * that is unavailable once this boundary replaces its children (e.g. hooks that need
   * an ancestor only present inside the subtree that threw). If the fallback throws,
   * the SDK logs via {@code PulseWebLogger.alwaysError} and renders nothing so the host
   * app is not crashed.
   */
  fallback?: ReactNode | ((error: Error, reset: () => void) => ReactNode);
}

/**
 * Minimal shape of `react-router-dom`'s location object that
 * {@link UseRouterTrackingOptions.format} receives. Avoids a hard type
 * dependency on `react-router-dom` at the public API surface.
 */
export interface PulseLocationLike {
  pathname: string;
  search: string;
  hash: string;
}

export interface UseRouterTrackingOptions {
  /**
   * Override how the location is converted to a screen name. Defaults to
   * `location.pathname` (or `pathname + search` when `includeSearch` is set).
   */
  format?: (location: PulseLocationLike) => string;
  /**
   * Include the query string in the screen name. Default `false` — query
   * strings often contain high-cardinality values (ids, tokens) and expand
   * the screen-name dimension on the dashboard.
   */
  includeSearch?: boolean;
  /**
   * Skip the very first render. Default `true` — the SDK already emits an
   * initial `session.start` / `screen.session` on `init()`, and the router
   * mount would otherwise cause a duplicate signal. Set `false` if you want
   * the hook to own the first screen name instead.
   */
  skipInitial?: boolean;
}

export interface PulseRouterEventsProps extends UseRouterTrackingOptions {
  /** Ignored — component renders null. Accepted for API symmetry with Next.js. */
  children?: never;
}

export interface PulseProviderProps {
  /**
   * SDK configuration. Captured on first mount only — subsequent changes are
   * ignored. To apply a new config, unmount and remount the provider.
   */
  config: PulseWebConfig;
  children: ReactNode;
  /**
   * Optional UI when a child throws during render — forwarded to {@link PulseErrorBoundary}.
   * Use a function form to receive {@code (error, reset) => ...} and call {@code reset()} to retry.
   * If this UI throws (e.g. {@code useNavigate} outside a Router), the SDK logs via
   * {@code PulseWebLogger.alwaysError} and renders nothing so the host app is not crashed;
   * see {@link PulseErrorBoundaryProps.fallback}.
   */
  errorBoundaryFallback?: PulseErrorBoundaryProps["fallback"];
  /**
   * If true, the SDK is shut down when the last `PulseProvider` unmounts.
   * Default **`false`** — keeps {@link Pulse} initialized for the full browser
   * tab even when React mounts/unmounts providers (SPA subtrees, micro-frontends,
   * route-level wrappers). Set **`true`** when you want strict teardown or in
   * tests — see `src/__tests__/pulse-provider.test.tsx` (`shutdownOnUnmount`
   * cases).
   *
   * StrictMode's synthetic unmount/remount in dev is handled automatically —
   * shutdown is deferred by a microtask and cancelled if the provider
   * re-mounts, so `init()` is never called twice.
   */
  shutdownOnUnmount?: boolean;
}
