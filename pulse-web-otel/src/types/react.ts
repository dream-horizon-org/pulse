import type { ReactNode } from "react";
import type { PulseWebConfig } from "../config";

export interface PulseErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode | ((error: Error, reset: () => void) => ReactNode);
}

export interface PulseProviderProps {
  /**
   * SDK configuration. Captured on first mount only — subsequent changes are
   * ignored. To apply a new config, unmount and remount the provider.
   */
  config: PulseWebConfig;
  children: ReactNode;
  /**
   * If true (default), the SDK is shut down when the last `PulseProvider`
   * unmounts. Set to `false` to keep the SDK alive for the full page lifetime
   * regardless of provider unmounts (recommended for most apps).
   *
   * StrictMode's synthetic unmount/remount in dev is handled automatically —
   * shutdown is deferred by a microtask and cancelled if the provider
   * re-mounts, so `start()` is never called twice.
   */
  shutdownOnUnmount?: boolean;
}

/** Subset of `react-router-dom`'s Location we use — avoids a hard import. */
export interface PulseLocationLike {
  pathname: string;
  search: string;
  hash: string;
}

export interface UseRouterTrackingOptions {
  /** Custom screen name builder — receives the current location. */
  format?: (location: PulseLocationLike) => string;
  /** Include `location.search` in the dependency key (default: false). */
  includeSearch?: boolean;
  /** Skip the very first render's call (default: true). */
  skipInitial?: boolean;
}
