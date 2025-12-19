import { AppState, type AppStateStatus } from 'react-native';
import type { RefObject } from 'react';
import type {
  NavigationContainer,
  NavigationIntegrationOptions,
  NavigationRoute,
} from './types';
import { pushRecentRouteKey, LOG_TAGS } from './utils';
import { createScreenLoadTracker, type ScreenLoadState } from './screen-load';
import {
  createScreenInteractiveTracker,
  markContentReady,
  clearGlobalMarkContentReady,
  type ScreenInteractiveState,
} from './screen-interactive';
import {
  createScreenSessionTracker,
  type ScreenSessionState,
} from './screen-session';
import { useNavigationTracking as useNavigationTrackingBase } from './hooks';

export type { NavigationRoute, NavigationIntegrationOptions };

export interface ReactNavigationIntegration {
  registerNavigationContainer: (
    maybeNavigationContainer: unknown
  ) => () => void;
  markContentReady: () => void;
}

export function createReactNavigationIntegration(
  options?: NavigationIntegrationOptions
): ReactNavigationIntegration {
  const screenSessionTracking = options?.screenSessionTracking ?? true;
  const screenNavigationTracking = options?.screenNavigationTracking ?? true;
  const screenInteractiveTracking = options?.screenInteractiveTracking ?? false;

  let navigationContainer: NavigationContainer | undefined;
  let recentRouteKeys: string[] = [];
  let isInitialized = false;
  let appStateSubscription: { remove: () => void } | undefined;

  const screenLoadState: ScreenLoadState = {
    navigationSpan: undefined,
    latestRoute: undefined,
  };

  const screenInteractiveState: ScreenInteractiveState = {
    screenInteractiveSpan: undefined,
    currentInteractiveRouteKey: undefined,
  };

  const screenSessionState: ScreenSessionState = {
    screenSessionSpan: undefined,
    currentScreenKey: undefined,
  };

  const screenInteractiveTracker = createScreenInteractiveTracker(
    screenInteractiveTracking,
    screenInteractiveState,
    navigationContainer
  );

  const screenLoadTracker = createScreenLoadTracker(
    screenNavigationTracking,
    screenLoadState,
    recentRouteKeys,
    (key: string) => {
      recentRouteKeys = pushRecentRouteKey(recentRouteKeys, key);
    },
    (route: NavigationRoute) => {
      if (screenInteractiveTracking) {
        screenInteractiveTracker.startScreenInteractive(route);
      }
    }
  );

  const screenSessionTracker = createScreenSessionTracker(
    screenSessionTracking,
    screenSessionState
  );

  const onNavigationDispatch = (): void => {
    try {
      if (screenInteractiveTracking) {
        screenInteractiveTracker.nullScreenInteractive('user navigated away');
      }

      if (
        screenSessionTracking &&
        screenSessionState.screenSessionSpan &&
        navigationContainer
      ) {
        const currentRoute = navigationContainer.getCurrentRoute();
        screenSessionTracker.endScreenSession(currentRoute?.name);
      }

      screenLoadTracker.startNavigationSpan();
    } catch (error) {
      console.warn(
        `${LOG_TAGS.NAVIGATION} Error in onNavigationDispatch:`,
        error
      );
      screenLoadState.navigationSpan = undefined;
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

      screenLoadState.latestRoute = currentRoute;

      screenLoadTracker.handleStateChange(currentRoute);

      const appState = AppState.currentState as AppStateStatus;
      if (
        appState &&
        screenSessionTracker.shouldStartSession(currentRoute, appState)
      ) {
        screenSessionTracker.startScreenSession(currentRoute);
      }
    } catch (error) {
      console.warn(`${LOG_TAGS.NAVIGATION} Error in onStateChange:`, error);
      screenLoadState.navigationSpan = undefined;
    }
  };

  const handleAppStateChange = (nextAppState: AppStateStatus): void => {
    try {
      screenSessionTracker.handleAppStateChange(
        nextAppState,
        navigationContainer
      );

      if (nextAppState === 'background' || nextAppState === 'inactive') {
        if (screenInteractiveTracking) {
          screenInteractiveTracker.nullScreenInteractive(
            'app went to background'
          );
        }
      }
    } catch (error) {
      console.warn(
        `${LOG_TAGS.NAVIGATION} Error in handleAppStateChange:`,
        error
      );
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
        console.warn(`${LOG_TAGS.NAVIGATION} Invalid navigation container ref`);
        return () => {};
      }

      if (isInitialized && navigationContainer === container) {
        return () => {
          if (screenSessionTracking && screenSessionState.screenSessionSpan) {
            const currentRoute = container.getCurrentRoute();
            screenSessionTracker.endScreenSession(currentRoute?.name);
          }
        };
      }

      navigationContainer = container;

      const updatedInteractiveTracker = createScreenInteractiveTracker(
        screenInteractiveTracking,
        screenInteractiveState,
        navigationContainer
      );

      navigationContainer.addListener(
        '__unsafe_action__',
        onNavigationDispatch
      );
      navigationContainer.addListener('state', onStateChange);

      const unmountCleanup = (): void => {
        if (screenSessionTracking && screenSessionState.screenSessionSpan) {
          const currentRoute = container.getCurrentRoute();
          screenSessionTracker.endScreenSession(currentRoute?.name);
        }

        if (screenInteractiveTracking) {
          screenInteractiveTracker.nullScreenInteractive(
            'navigation container unmounted'
          );
        }

        screenLoadTracker.endNavigationSpan();

        if (navigationContainer === container) {
          if (appStateSubscription) {
            appStateSubscription.remove();
            appStateSubscription = undefined;
          }
          navigationContainer = undefined;
          isInitialized = false;

          clearGlobalMarkContentReady(
            updatedInteractiveTracker.markContentReady
          );
        }
      };

      const currentRoute = container.getCurrentRoute();
      if (currentRoute) {
        screenLoadState.latestRoute = currentRoute;
        recentRouteKeys = pushRecentRouteKey(recentRouteKeys, currentRoute.key);

        const appState = AppState.currentState as AppStateStatus;
        if (
          appState &&
          screenSessionTracker.shouldStartSession(currentRoute, appState)
        ) {
          screenSessionTracker.startScreenSession(currentRoute);
        }
      }

      appStateSubscription = AppState.addEventListener(
        'change',
        handleAppStateChange
      );
      isInitialized = true;

      return unmountCleanup;
    } catch (error) {
      console.error(
        `${LOG_TAGS.NAVIGATION} Error registering container:`,
        error
      );
      return () => {};
    }
  };

  return {
    registerNavigationContainer,
    markContentReady: screenInteractiveTracker.markContentReady,
  };
}

export { markContentReady };

export function useNavigationTracking(
  navigationRef: RefObject<any>,
  options?: NavigationIntegrationOptions
): () => void {
  const { createNavigationIntegrationWithConfig } = require('../config');
  return useNavigationTrackingBase(
    navigationRef,
    options,
    createNavigationIntegrationWithConfig
  );
}
