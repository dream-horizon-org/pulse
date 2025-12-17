import { Pulse, type Span } from './index';
import { AppState, type AppStateStatus } from 'react-native';
import { useRef, useCallback, useEffect, useMemo, type RefObject } from 'react';
import { createNavigationIntegrationWithConfig } from './config';

const NAVIGATION_HISTORY_MAX_SIZE = 200;

export interface NavigationRoute {
  name: string;
  key: string;
  params?: Record<string, any>;
}

interface NavigationContainer {
  addListener: (
    type: string,
    listener: (event?: unknown) => void
  ) => { remove: () => void } | void;
  getCurrentRoute: () => NavigationRoute | undefined;
}

export interface NavigationIntegrationOptions {
  enableScreenSession?: boolean;
}

export function createReactNavigationIntegration(
  options?: NavigationIntegrationOptions
) {
  const enableScreenSession = options?.enableScreenSession ?? true;
  let navigationContainer: NavigationContainer | undefined;
  let latestRoute: NavigationRoute | undefined;
  let recentRouteKeys: string[] = [];
  let isInitialized = false;
  let navigationSpan: Span | undefined;
  let screenSessionSpan: Span | undefined;
  let currentScreenKey: string | undefined;
  let appStateSubscription: { remove: () => void } | undefined;

  const startScreenSession = (route: NavigationRoute): void => {
    screenSessionSpan = Pulse.startSpan('ScreenSession', {
      attributes: {
        'pulse.type': 'screen_session',
        'screen.name': route.name,
        'routeKey': route.key,
      },
    });
    currentScreenKey = route.key;
  };

  const endScreenSession = (): void => {
    if (screenSessionSpan) {
      screenSessionSpan.end();
      screenSessionSpan = undefined;
      currentScreenKey = undefined;
    }
  };

  const endNavigationSpan = (): void => {
    if (navigationSpan) {
      navigationSpan.end();
      navigationSpan = undefined;
    }
  };

  const onNavigationDispatch = (): void => {
    try {
      if (enableScreenSession && screenSessionSpan && navigationContainer) {
        const currentRoute = navigationContainer.getCurrentRoute();
        console.log(`[Pulse] Screen session ended for ${currentRoute?.name || 'unknown'}`);
        endScreenSession();
      }

      navigationSpan = Pulse.startSpan('Navigated', {
        attributes: {
          'pulse.type': 'screen_load',
          'phase': 'start',
        },
      });
      console.log('[Pulse Navigation] Navigation span started', navigationSpan?.spanId);
    } catch (error) {
      console.warn('[Pulse Navigation] Error in onNavigationDispatch:', error);
      navigationSpan = undefined;
    }
  };

  const onStateChange = (): void => {
    try {
      if (!navigationContainer) {
        return;
      }

      const currentRoute = navigationContainer.getCurrentRoute();
      if (!currentRoute) {
        return;
      }

      const previousRoute = latestRoute;

      if (navigationSpan) {
        const spanId = navigationSpan.spanId;
        if (previousRoute && previousRoute.key === currentRoute.key) {
          endNavigationSpan();
          console.log('[Pulse Navigation] Navigation span ended', spanId);
        } else {
          latestRoute = currentRoute;
          const routeHasBeenSeen = recentRouteKeys.includes(currentRoute.key);
          pushRecentRouteKey(currentRoute.key);

          navigationSpan.setAttributes({
            'screen.name': currentRoute.name,
            'last.screen.name': previousRoute?.name || undefined,
            'routeHasBeenSeen': routeHasBeenSeen,
            'routeKey': currentRoute.key,
          });

          endNavigationSpan();
          console.log('[Pulse Navigation] Navigation span ended', spanId);
        }
      } else {
        latestRoute = currentRoute;
        pushRecentRouteKey(currentRoute.key);
      }

      if (
        enableScreenSession &&
        AppState.currentState === 'active' &&
        !screenSessionSpan &&
        currentScreenKey !== currentRoute.key
      ) {
        startScreenSession(currentRoute);
        console.log(`[Pulse] Screen session started on ${currentRoute.name}`);
      }
    } catch (error) {
      console.warn('[Pulse] Error in onStateChange:', error);
      navigationSpan = undefined;
    }
  };

  const handleAppStateChange = (nextAppState: AppStateStatus): void => {
    try {
      if (!enableScreenSession) {
        return;
      }

      if (nextAppState === 'background' || nextAppState === 'inactive') {
        if (screenSessionSpan) {
          const currentRoute = navigationContainer?.getCurrentRoute();
          console.log(`[Pulse] Screen session ended for ${currentRoute?.name || 'unknown'} (app backgrounded)`);
          endScreenSession();
        }
      } else if (nextAppState === 'active') {
        const currentRoute = navigationContainer?.getCurrentRoute();
        if (currentRoute && !screenSessionSpan) {
          startScreenSession(currentRoute);
          console.log(`[Pulse] Screen session started on ${currentRoute.name} (app foregrounded)`);
        }
      }
    } catch (error) {
      console.warn('[Pulse] Error in handleAppStateChange:', error);
    }
  };

  const registerNavigationContainer = (
    maybeNavigationContainer: unknown
  ): (() => void) => {
    try {
      let container: NavigationContainer | undefined;
      if (
        typeof maybeNavigationContainer === 'object' &&
        maybeNavigationContainer !== null &&
        'current' in maybeNavigationContainer
      ) {
        container = maybeNavigationContainer.current as NavigationContainer;
      } else {
        container = maybeNavigationContainer as NavigationContainer;
      }

      if (!container) {
        console.warn('[Pulse Navigation] Invalid navigation container ref');
        return () => {};
      }

      if (isInitialized && navigationContainer === container) {
        console.log('[Pulse Navigation] Container already registered');
        return () => {
          if (enableScreenSession && screenSessionSpan) {
            const currentRoute = navigationContainer?.getCurrentRoute();
            console.log(`[Pulse] Screen session ended for ${currentRoute?.name || 'unknown'} (container cleanup)`);
            endScreenSession();
          }
        };
      }

      navigationContainer = container;

      container.addListener('__unsafe_action__', onNavigationDispatch);
      container.addListener('state', onStateChange);

      const unmountCleanup = (): void => {
        // React Navigation automatically removes listeners on unmount, so we only need to clean up our state
        if (enableScreenSession && screenSessionSpan) {
          const route = navigationContainer?.getCurrentRoute();
          console.log(`[Pulse] Screen session ended for ${route?.name || 'unknown'} (container unmounting)`);
          endScreenSession();
        }

        endNavigationSpan();

        if (navigationContainer === container) {
          if (appStateSubscription) {
            appStateSubscription.remove();
            appStateSubscription = undefined;
          }
          navigationContainer = undefined;
          isInitialized = false;
        }
      };

      const currentRoute = container.getCurrentRoute();
      if (currentRoute) {
        latestRoute = currentRoute;
        pushRecentRouteKey(currentRoute.key);

        if (
          enableScreenSession &&
          AppState.currentState === 'active' &&
          !screenSessionSpan &&
          currentScreenKey !== currentRoute.key
        ) {
          startScreenSession(currentRoute);
          console.log(`[Pulse] Screen session started on ${currentRoute.name}`);
        }
      }

      appStateSubscription = AppState.addEventListener('change', handleAppStateChange);
      isInitialized = true;

      return unmountCleanup;
    } catch (error) {
      console.error('[Pulse Navigation] Error registering container:', error);
      return () => {};
    }
  };

  const pushRecentRouteKey = (key: string): void => {
    recentRouteKeys.push(key);
    if (recentRouteKeys.length > NAVIGATION_HISTORY_MAX_SIZE) {
      recentRouteKeys = recentRouteKeys.slice(
        recentRouteKeys.length - NAVIGATION_HISTORY_MAX_SIZE
      );
    }
  };

  return {
    registerNavigationContainer,
  };
}

export type ReactNavigationIntegration = ReturnType<
  typeof createReactNavigationIntegration
>;

/**
 * React hook to automatically register NavigationContainer with Pulse.
 * Returns an onReady callback that should be passed to NavigationContainer.
 * This simplifies usage - no need to manually manage cleanup.
 * 
 * @example
 * const navigationRef = useRef(null);
 * const onReady = Pulse.useNavigationTracking(navigationRef);
 * return <NavigationContainer ref={navigationRef} onReady={onReady}>...
 */
export function useNavigationTracking(
  navigationRef: RefObject<any>,
  options?: NavigationIntegrationOptions
): () => void {
  // Create integration synchronously to avoid race condition with onReady
  const integration = useMemo(
    () => createNavigationIntegrationWithConfig(options),
    [options?.enableScreenSession]
  );

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
      cleanupRef.current = integration.registerNavigationContainer(navigationRef);
    }
  }, [navigationRef, integration]);

  return onReady;
}
