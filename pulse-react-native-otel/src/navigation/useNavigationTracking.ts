import { useRef, useCallback, useEffect, useMemo, type RefObject } from 'react';
import {
  DEFAULT_NAVIGATION_OPTIONS,
  type NavigationIntegrationOptions,
} from './navigation.interface';
import type { ReactNavigationIntegration } from './index';

export function useNavigationTracking(
  navigationRef: RefObject<any>,
  options?: NavigationIntegrationOptions,
  createIntegration?: (
    options?: NavigationIntegrationOptions
  ) => ReactNavigationIntegration
): () => void {
  const screenSessionTracking =
    options?.screenSessionTracking ??
    DEFAULT_NAVIGATION_OPTIONS.screenSessionTracking;
  const screenNavigationTracking =
    options?.screenNavigationTracking ??
    DEFAULT_NAVIGATION_OPTIONS.screenNavigationTracking;
  const screenInteractiveTracking =
    options?.screenInteractiveTracking ??
    DEFAULT_NAVIGATION_OPTIONS.screenInteractiveTracking;

  const integration = useMemo(() => {
    if (createIntegration) {
      return createIntegration({
        screenSessionTracking,
        screenNavigationTracking,
        screenInteractiveTracking,
      });
    }
    throw new Error('createIntegration must be provided');
  }, [
    screenSessionTracking,
    screenNavigationTracking,
    screenInteractiveTracking,
    createIntegration,
  ]);

  const cleanupRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    return () => {
      if (cleanupRef.current) {
        cleanupRef.current();
      }
    };
  }, []);

  const onReady = useCallback(() => {
    if (navigationRef.current && integration) {
      cleanupRef.current =
        integration.registerNavigationContainer(navigationRef);
    }
  }, [navigationRef, integration]);

  return onReady;
}
