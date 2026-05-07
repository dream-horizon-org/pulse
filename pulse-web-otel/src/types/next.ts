/**
 * Types for the @dreamhorizon/pulse-web/next subpath.
 */

/** Normalized location passed to the `format` callback in Next.js hooks. */
export interface PulseNextLocationLike {
  pathname: string;
  /** Query string without the leading "?". Empty string when no query. */
  search: string;
  /** Hash fragment without the leading "#". Always "" in App Router. */
  hash: string;
}

export interface UseNextAppRouterTrackingOptions {
  /** Include the query string in the screen name dependency (triggers on `?foo=1` changes). */
  includeSearch?: boolean;
  /** Skip calling setScreenName on the very first mount. Default: true. */
  skipInitial?: boolean;
  /** Custom formatter — return the desired screen name string. */
  format?: (loc: PulseNextLocationLike) => string;
}

export interface UseNextPagesRouterTrackingOptions {
  /** Include the full URL (pathname + query) as the screen name. Default: false (pathname only). */
  includeSearch?: boolean;
  /** Skip calling setScreenName on the very first routeChangeComplete event. Default: false. */
  skipInitial?: boolean;
  /** Custom formatter — return the desired screen name string. */
  format?: (loc: PulseNextLocationLike) => string;
}

export interface PulseRouterEventsProps
  extends UseNextAppRouterTrackingOptions {
  /** Ignored — component renders null. Accepted for convenience in layouts. */
  children?: never;
}
