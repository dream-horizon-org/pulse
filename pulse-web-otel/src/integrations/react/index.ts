export {
  PulseProvider,
  usePulse,
  type PulseContextValue,
  type PulseProviderProps,
} from "../react/PulseProvider";

export {
  PulseErrorBoundary,
  type PulseErrorBoundaryProps,
} from "../react/PulseErrorBoundary";

export {
  PulseRouterEvents,
  type PulseRouterEventsProps,
} from "./PulseRouterEvents";

// React Router v6 hooks live at @dreamhorizonorg/pulse-web/react/router
// (separate sub-entrypoint so react-router-dom is not required by apps
// that only need PulseProvider / usePulse).
