/**
 * React Router v6 sub-entrypoint — @dreamhorizonorg/pulse-web/react/router
 *
 * Intentionally separate from /react so apps that do NOT use React Router
 * are never forced to install react-router-dom.
 *
 * Peer dependency: react-router-dom >=6.0.0
 *
 * Usage:
 *   import { useRouterTracking, PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";
 */
export {
  useRouterTracking,
  type UseRouterTrackingOptions,
} from "./useRouterTracking";

export {
  PulseRouterEvents,
  type PulseRouterEventsProps,
} from "./PulseRouterEvents";

export {
  PulseIntegrationErrorBoundary,
  type PulseIntegrationErrorContext,
} from "./pulse-integration-error-boundary";
