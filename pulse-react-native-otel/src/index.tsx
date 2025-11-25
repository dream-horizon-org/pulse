import { startSpan, trackSpan } from './trace';
import { reportException } from './errorHandler';
import { trackEvent } from './events';
import { start, createNavigationIntegrationWithConfig } from './config';
import { isInitialized } from './initialization';
import { setGlobalAttribute } from './globalAttributes';
import { setUserId, setUserProperty, setUserProperties } from './user';
import { ErrorBoundary, withErrorBoundary } from './errorBoundary';

export type { Span } from './trace';
export type { PulseConfig, PulseStartOptions } from './config';
export type { PulseAttributes, PulseAttributeValue } from './pulse.interface';
export type {
  ReactNavigationIntegration,
  NavigationRoute,
} from './reactNavigation';

export type { ErrorBoundaryProps, FallbackRender } from './errorBoundary';

export { SpanStatusCode } from './trace';
export const Pulse = {
  start,
  isInitialized,
  createNavigationIntegration: createNavigationIntegrationWithConfig,
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
