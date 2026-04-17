import { startSpan, trackSpan } from './trace';
import { reportException } from './errorHandler';
import { trackEvent } from './events';
import {
  start,
  shutdown,
  setDataCollectionState,
  createNavigationIntegrationWithConfig,
} from './config';
import { isInitialized } from './initialization';
import { setGlobalAttribute } from './globalAttributes';
import { setUserId, setUserProperty, setUserProperties } from './user';
import { ErrorBoundary, withErrorBoundary } from './errorBoundary';
import { markContentReady } from './navigation';
import { useNavigationTracking as useNavigationTrackingBase } from './navigation/useNavigationTracking';
import type { RefObject } from 'react';
import type { NavigationIntegrationOptions } from './navigation';
import { PulseMask, PulseUnmask } from './sessionReplay';

export type { Span } from './trace';
export type { PulseConfig } from './config';
export type { NetworkHeaderConfig } from './config';
export type { PulseAttributes, PulseAttributeValue } from './pulse.interface';
export type {
  ReactNavigationIntegration,
  NavigationRoute,
  NavigationIntegrationOptions,
} from './navigation';

export function useNavigationTracking(
  navigationRef: RefObject<any>,
  options?: NavigationIntegrationOptions
): () => void {
  return useNavigationTrackingBase(
    navigationRef,
    options,
    createNavigationIntegrationWithConfig
  );
}

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

export { PulseMask, PulseUnmask };
