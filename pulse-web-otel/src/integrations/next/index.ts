/**
 * @dreamhorizon/pulse-web/next
 *
 * Next.js integration for the Pulse Web SDK.
 * Re-exports PulseProvider, usePulse, PulseErrorBoundary from /react
 * and adds Next.js-specific hooks and components.
 */

export {
  useNextAppRouterTracking,
  type UseNextAppRouterTrackingOptions,
} from "./useNextAppRouterTracking";

export {
  useNextPagesRouterTracking,
  type UseNextPagesRouterTrackingOptions,
} from "./useNextPagesRouterTracking";

export {
  PulseRouterEvents,
  type PulseRouterEventsProps,
} from "./PulseRouterEvents";

export {
  createPulseInstrumentationHandler,
  type PulseInstrumentationConfig,
} from "./instrumentation";

// Re-export React integration primitives so consumers only need one import.
export {
  PulseProvider,
  usePulse,
  type PulseProviderProps,
  type PulseContextValue,
} from "../react/PulseProvider";

export {
  PulseErrorBoundary,
  type PulseErrorBoundaryProps,
} from "../react/PulseErrorBoundary";
