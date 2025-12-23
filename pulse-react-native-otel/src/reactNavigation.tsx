import { Pulse, type Span } from './index';
import { AppState, type AppStateStatus, Platform } from 'react-native';
import { useRef, useCallback, useEffect, useMemo, type RefObject } from 'react';
import { createNavigationIntegrationWithConfig } from './config';
import PulseReactNativeOtel from './NativePulseReactNativeOtel';
import { isSupportedPlatform } from './initialization';
import {
  SPAN_NAMES,
  ATTRIBUTE_KEYS,
  PULSE_TYPES,
  PHASE_VALUES,
} from './pulse.constants';

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
  screenSessionTracking?: boolean;
  screenNavigationTracking?: boolean;
}

export function createReactNavigationIntegration(
  options?: NavigationIntegrationOptions
) {
  const screenSessionTracking = options?.screenSessionTracking ?? true;
  const screenNavigationTracking = options?.screenNavigationTracking ?? true;
  let navigationContainer: NavigationContainer | undefined;
  let latestRoute: NavigationRoute | undefined;
  let recentRouteKeys: string[] = [];
  let isInitialized = false;
  let navigationSpan: Span | undefined;
  let screenSessionSpan: Span | undefined;
  let currentScreenKey: string | undefined;
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

  const setCurrentScreenName = (screenName: string): void => {
    if (isSupportedPlatform()) {
      PulseReactNativeOtel.setCurrentScreenName(screenName);
    }
  };

  const onNavigationDispatch = (): void => {
    try {
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

      if (previousRoute && previousRoute.key !== currentRoute.key) {
        setCurrentScreenName(currentRoute.name);
      }

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
      if (!screenSessionTracking) {
        return;
      }

      if (nextAppState === 'background' || nextAppState === 'inactive') {
        if (screenSessionSpan) {
          endScreenSession();
        }
      } else if (nextAppState === 'active') {
        const currentRoute = navigationContainer?.getCurrentRoute();
        if (currentRoute && !screenSessionSpan) {
          startScreenSession(currentRoute);
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

        setCurrentScreenName(currentRoute.name);

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

  return {
    registerNavigationContainer,
  };
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

  const integration = useMemo(
    () =>
      createNavigationIntegrationWithConfig({
        screenSessionTracking,
        screenNavigationTracking,
      }),
    [screenSessionTracking, screenNavigationTracking]
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
