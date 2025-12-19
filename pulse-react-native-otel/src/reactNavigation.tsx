import { Pulse, type Span } from './index';
import { AppState, type AppStateStatus, Platform } from 'react-native';
import { useRef, useCallback, useEffect, useMemo, type RefObject } from 'react';
import { createNavigationIntegrationWithConfig } from './config';
import {
  SPAN_NAMES,
  ATTRIBUTE_KEYS,
  PULSE_TYPES,
  PHASE_VALUES,
} from './pulse.constants';

const NAVIGATION_HISTORY_MAX_SIZE = 200;

// Global reference to markContentReady function
let globalMarkContentReady: (() => void) | undefined;

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
  screenSessionTracking?: boolean;
  screenNavigationTracking?: boolean;
  screenInteractiveTracking?: boolean;
}

export function createReactNavigationIntegration(
  options?: NavigationIntegrationOptions
) {
  const screenSessionTracking = options?.screenSessionTracking ?? true;
  const screenNavigationTracking = options?.screenNavigationTracking ?? true;
  const screenInteractiveTracking = options?.screenInteractiveTracking ?? false;
  let navigationContainer: NavigationContainer | undefined;
  let latestRoute: NavigationRoute | undefined;
  let recentRouteKeys: string[] = [];
  let isInitialized = false;
  let navigationSpan: Span | undefined;
  let screenSessionSpan: Span | undefined;
  let screenInteractiveSpan: Span | undefined;
  let currentScreenKey: string | undefined;
  let currentInteractiveRouteKey: string | undefined;
  let appStateSubscription: { remove: () => void } | undefined;

  const startScreenSession = (route: NavigationRoute): void => {
    screenSessionSpan = Pulse.startSpan(SPAN_NAMES.SCREEN_SESSION, {
      attributes: {
        [ATTRIBUTE_KEYS.PULSE_TYPE]: PULSE_TYPES.SCREEN_SESSION,
        [ATTRIBUTE_KEYS.SCREEN_NAME]: route.name,
        [ATTRIBUTE_KEYS.ROUTE_KEY]: route.key,
        [ATTRIBUTE_KEYS.PLATFORM]: Platform.OS as 'android' | 'ios',
      },
    });
    currentScreenKey = route.key;
    console.log(
      `[Pulse Navigation] [TEST] Started screen_session span for screen: ${route.name}, routeKey: ${route.key}`
    );
  };

  const endScreenSession = (): void => {
    if (screenSessionSpan) {
      const endedKey = currentScreenKey;
      screenSessionSpan.end();
      console.log(
        `[Pulse Navigation] [TEST] Ended screen_session span for routeKey: ${endedKey}`
      );
      screenSessionSpan = undefined;
      currentScreenKey = undefined;
    }
  };

  const startScreenInteractive = (route: NavigationRoute): void => {
    if (!screenInteractiveTracking) {
      return;
    }

    // Null any existing interactive span before starting a new one
    if (screenInteractiveSpan) {
      console.log(
        `[Pulse Navigation] [TEST] Nulling existing screen_interactive span for route: ${currentInteractiveRouteKey}`
      );
      screenInteractiveSpan = undefined;
      currentInteractiveRouteKey = undefined;
    }

    screenInteractiveSpan = Pulse.startSpan(SPAN_NAMES.SCREEN_INTERACTIVE, {
      attributes: {
        [ATTRIBUTE_KEYS.PULSE_TYPE]: PULSE_TYPES.SCREEN_INTERACTIVE,
        [ATTRIBUTE_KEYS.SCREEN_NAME]: route.name,
        [ATTRIBUTE_KEYS.ROUTE_KEY]: route.key,
        [ATTRIBUTE_KEYS.PLATFORM]: Platform.OS as 'android' | 'ios',
      },
    });
    currentInteractiveRouteKey = route.key;
    console.log(
      `[Pulse Navigation] [TEST] Started screen_interactive span for screen: ${route.name}, routeKey: ${route.key}`
    );
  };

  const endScreenInteractive = (): void => {
    if (screenInteractiveSpan) {
      screenInteractiveSpan.end();
      console.log(
        `[Pulse Navigation] [TEST] Ended screen_interactive span for routeKey: ${currentInteractiveRouteKey}`
      );
      screenInteractiveSpan = undefined;
      currentInteractiveRouteKey = undefined;
    }
  };

  const nullScreenInteractive = (reason: string): void => {
    if (screenInteractiveSpan) {
      console.log(
        `[Pulse Navigation] [TEST] Nulling screen_interactive span (${reason}) for routeKey: ${currentInteractiveRouteKey}`
      );
      screenInteractiveSpan = undefined;
      currentInteractiveRouteKey = undefined;
    }
  };

  const endNavigationSpan = (): void => {
    if (navigationSpan) {
      const route = latestRoute;
      navigationSpan.end();
      console.log(
        `[Pulse Navigation] [TEST] Ended screen_load span for screen: ${route?.name || 'unknown'}`
      );
      navigationSpan = undefined;

      // Automatically start screen_interactive span when screen_load ends
      if (screenInteractiveTracking && route) {
        startScreenInteractive(route);
      }
    }
  };

  const onNavigationDispatch = (): void => {
    try {
      // Null interactive span when navigation starts (user navigates away)
      if (screenInteractiveTracking) {
        nullScreenInteractive('navigation started');
      }

      if (screenSessionTracking && screenSessionSpan && navigationContainer) {
        endScreenSession();
      }

      if (screenNavigationTracking) {
        navigationSpan = Pulse.startSpan(SPAN_NAMES.NAVIGATED, {
          attributes: {
            [ATTRIBUTE_KEYS.PULSE_TYPE]: PULSE_TYPES.SCREEN_LOAD,
            [ATTRIBUTE_KEYS.PHASE]: PHASE_VALUES.START,
            [ATTRIBUTE_KEYS.PLATFORM]: Platform.OS as 'android' | 'ios',
          },
        });
        console.log(
          `[Pulse Navigation] [TEST] Started screen_load span (navigation dispatch)`
        );
      }
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

      if (screenNavigationTracking && navigationSpan) {
        if (previousRoute && previousRoute.key === currentRoute.key) {
          endNavigationSpan();
        } else {
          latestRoute = currentRoute;
          const routeHasBeenSeen = recentRouteKeys.includes(currentRoute.key);
          pushRecentRouteKey(currentRoute.key);

          navigationSpan.setAttributes({
            [ATTRIBUTE_KEYS.SCREEN_NAME]: currentRoute.name,
            [ATTRIBUTE_KEYS.LAST_SCREEN_NAME]: previousRoute?.name || undefined,
            [ATTRIBUTE_KEYS.ROUTE_HAS_BEEN_SEEN]: routeHasBeenSeen,
            [ATTRIBUTE_KEYS.ROUTE_KEY]: currentRoute.key,
          });

          console.log(
            `[Pulse Navigation] [TEST] screen_load span attributes set - screen: ${currentRoute.name}, from: ${previousRoute?.name || 'none'}, routeKey: ${currentRoute.key}`
          );

          endNavigationSpan();
        }
      } else {
        latestRoute = currentRoute;
        pushRecentRouteKey(currentRoute.key);
      }

      if (
        screenSessionTracking &&
        AppState.currentState === 'active' &&
        !screenSessionSpan &&
        currentScreenKey !== currentRoute.key
      ) {
        startScreenSession(currentRoute);
      }
    } catch (error) {
      console.warn('[Pulse] Error in onStateChange:', error);
      navigationSpan = undefined;
    }
  };

  const handleAppStateChange = (nextAppState: AppStateStatus): void => {
    try {
      if (nextAppState === 'background' || nextAppState === 'inactive') {
        if (screenSessionTracking && screenSessionSpan) {
          console.log(
            `[Pulse Navigation] [TEST] App backgrounded - ending screen_session span for routeKey: ${currentScreenKey}`
          );
          endScreenSession();
        }
        // Null interactive span when app goes to background
        if (screenInteractiveTracking) {
          nullScreenInteractive('app backgrounded');
        }
      } else if (nextAppState === 'active') {
        if (screenSessionTracking) {
          const currentRoute = navigationContainer?.getCurrentRoute();
          if (currentRoute && !screenSessionSpan) {
            console.log(
              `[Pulse Navigation] [TEST] App foregrounded - starting screen_session span for screen: ${currentRoute.name}, routeKey: ${currentRoute.key}`
            );
            startScreenSession(currentRoute);
          }
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
        return () => {
          if (screenSessionTracking && screenSessionSpan) {
            endScreenSession();
          }
        };
      }

      navigationContainer = container;

      navigationContainer.addListener(
        '__unsafe_action__',
        onNavigationDispatch
      );
      navigationContainer.addListener('state', onStateChange);

      const unmountCleanup = (): void => {
        if (screenSessionTracking && screenSessionSpan) {
          endScreenSession();
        }

        // Null interactive span on unmount
        if (screenInteractiveTracking) {
          nullScreenInteractive('unmount');
        }

        endNavigationSpan();

        if (navigationContainer === container) {
          if (appStateSubscription) {
            appStateSubscription.remove();
            appStateSubscription = undefined;
          }
          navigationContainer = undefined;
          isInitialized = false;
          // Clear global markContentReady when this integration is cleaned up
          if (globalMarkContentReady === markContentReady) {
            globalMarkContentReady = undefined;
          }
        }
      };

      const currentRoute = container.getCurrentRoute();
      if (currentRoute) {
        latestRoute = currentRoute;
        pushRecentRouteKey(currentRoute.key);

        if (
          screenSessionTracking &&
          AppState.currentState === 'active' &&
          !screenSessionSpan &&
          currentScreenKey !== currentRoute.key
        ) {
          startScreenSession(currentRoute);
        }
      }

      appStateSubscription = AppState.addEventListener(
        'change',
        handleAppStateChange
      );
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

  const markContentReady = (): void => {
    try {
      if (!screenInteractiveTracking) {
        console.warn(
          '[Pulse Navigation] [DEBUG] markContentReady called but screenInteractiveTracking is disabled'
        );
        return;
      }

      if (!screenInteractiveSpan) {
        console.log(
          '[Pulse Navigation] [TEST] markContentReady called but no active screen_interactive span (may have been nulled or not started yet)'
        );
        return;
      }

      // Validate current route matches the span's route key
      const currentRoute = navigationContainer?.getCurrentRoute();
      if (!currentRoute) {
        console.warn(
          '[Pulse Navigation] [DEBUG] markContentReady called but no current route found'
        );
        nullScreenInteractive('no current route');
        return;
      }

      if (currentRoute.key !== currentInteractiveRouteKey) {
        console.warn(
          `[Pulse Navigation] [DEBUG] markContentReady called for wrong screen. Expected: ${currentInteractiveRouteKey}, Current: ${currentRoute.key}`
        );
        nullScreenInteractive('route mismatch');
        return;
      }

      endScreenInteractive();
    } catch (error) {
      console.error('[Pulse Navigation] [DEBUG] Error in markContentReady:', error);
    }
  };

  // Store markContentReady globally for external access
  globalMarkContentReady = markContentReady;

  return {
    registerNavigationContainer,
    markContentReady,
  };
}

export function markContentReady(): void {
  if (globalMarkContentReady) {
    globalMarkContentReady();
  } else {
    console.warn(
      '[Pulse Navigation] markContentReady called but navigation integration not initialized'
    );
  }
}

export type ReactNavigationIntegration = ReturnType<
  typeof createReactNavigationIntegration
>;

export function useNavigationTracking(
  navigationRef: RefObject<any>,
  options?: NavigationIntegrationOptions
): () => void {
  const screenSessionTracking = options?.screenSessionTracking ?? true;
  const screenNavigationTracking = options?.screenNavigationTracking ?? true;
  const screenInteractiveTracking = options?.screenInteractiveTracking ?? false;

  const integration = useMemo(
    () =>
      createNavigationIntegrationWithConfig({
        screenSessionTracking,
        screenNavigationTracking,
        screenInteractiveTracking,
      }),
    [screenSessionTracking, screenNavigationTracking, screenInteractiveTracking]
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
      cleanupRef.current =
        integration.registerNavigationContainer(navigationRef);
    }
  }, [navigationRef, integration]);

  return onReady;
}
