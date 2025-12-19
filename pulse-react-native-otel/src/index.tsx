import { startSpan, trackSpan } from './trace';
import { reportException } from './errorHandler';
import { trackEvent } from './events';
import { start } from './config';
import { isInitialized } from './initialization';
import { setGlobalAttribute } from './globalAttributes';
import { setUserId, setUserProperty, setUserProperties } from './user';
import { ErrorBoundary, withErrorBoundary } from './errorBoundary';
import { markContentReady, type NavigationIntegrationOptions } from './navigation';
import { createNavigationIntegrationWithConfig } from './config';
import { useNavigationTracking as useNavigationTrackingBase } from './navigation/hooks';
import type { RefObject } from 'react';

const useNavigationTracking = (
  navigationRef: RefObject<any>,
  options?: NavigationIntegrationOptions
) => {
  return useNavigationTrackingBase(
    navigationRef,
    options,
    createNavigationIntegrationWithConfig
  );
};

export type { Span } from './trace';
export type { PulseConfig, PulseStartOptions } from './config';
export type { PulseAttributes, PulseAttributeValue } from './pulse.interface';
export type {
  ReactNavigationIntegration,
  NavigationRoute,
  NavigationIntegrationOptions,
} from './navigation';

export type { ErrorBoundaryProps, FallbackRender } from './errorBoundary';

export { SpanStatusCode } from './trace';
export const Pulse = {
  start,
  isInitialized,
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
