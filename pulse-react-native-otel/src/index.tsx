import { startSpan, trackSpan } from './trace';
import { reportException } from './errorHandler';
import { trackEvent } from './events';
import { start, shutdown, setDataCollectionState } from './config';
import { isInitialized } from './initialization';
import { setGlobalAttribute } from './globalAttributes';
import { setUserId, setUserProperty, setUserProperties } from './user';
import { ErrorBoundary, withErrorBoundary } from './errorBoundary';
import { useNavigationTracking, markContentReady } from './navigation';

export type { Span } from './trace';
export type { PulseConfig } from './config';
export type { NetworkHeaderConfig } from './config';
export type { PulseAttributes, PulseAttributeValue } from './pulse.interface';
export type {
  ReactNavigationIntegration,
  NavigationRoute,
  NavigationIntegrationOptions,
} from './navigation';

export type { ErrorBoundaryProps, FallbackRender } from './errorBoundary';

export { SpanStatusCode } from './trace';
export { PulseDataCollectionConsent } from './NativePulseReactNativeOtel';

export const Pulse = {
  start,
  shutdown,
  isInitialized,
  setDataCollectionState,
  useNavigationTracking,
  markContentReady,
  trackEvent,
  reportException,
  trackSpan,
  startSpan,
  setGlobalAttribute,
  setUserId,
  setUserProperty,
  setUserProperties,
  ErrorBoundary,
  withErrorBoundary,
};
