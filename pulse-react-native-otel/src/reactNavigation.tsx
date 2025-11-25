import { Pulse, type Span } from './index';

const NAVIGATION_HISTORY_MAX_SIZE = 200;

export interface NavigationRoute {
  name: string;
  key: string;
  params?: Record<string, any>;
}

interface NavigationContainer {
  addListener: (type: string, listener: (event?: unknown) => void) => void;
  getCurrentRoute: () => NavigationRoute | undefined;
}

export function createReactNavigationIntegration() {
  let navigationContainer: NavigationContainer | undefined;
  let latestRoute: NavigationRoute | undefined;
  let recentRouteKeys: string[] = [];
  let isInitialized = false;
  let span: Span | undefined;

  const onNavigationDispatch = (): void => {
    try {
      span = Pulse.startSpan('Navigated', {
        attributes: {
          'pulse.type': 'screen_load',
          'phase': 'start',
        },
      });

      console.log('[Pulse Navigation] Navigation dispatch span started', span);
    } catch (error) {
      console.warn('[Pulse Navigation] Error in onNavigationDispatch:', error);
    }
  };

  const onStateChange = (): void => {
    try {
      if (!navigationContainer) {
        console.warn('[Pulse Navigation] Navigation container not registered');
        return;
      }

      const currentRoute = navigationContainer.getCurrentRoute();
      if (!currentRoute) {
        return;
      }

      if (!span) {
        latestRoute = currentRoute;
        pushRecentRouteKey(currentRoute.key);
        return;
      }

      const previousRoute = latestRoute;

      if (previousRoute && previousRoute.key === currentRoute.key) {
        return;
      }

      latestRoute = currentRoute;

      const routeHasBeenSeen = recentRouteKeys.includes(currentRoute.key);
      pushRecentRouteKey(currentRoute.key);

      span?.setAttributes({
        'screen.name': currentRoute.name,
        'last.screen.name': previousRoute?.name || undefined,
        'routeHasBeenSeen': routeHasBeenSeen,
        'routeKey': currentRoute.key,
      });

      span?.end();
      console.log('[Pulse Navigation] Navigation dispatch span ended', span);
      span = undefined;
    } catch (error) {
      console.warn('[Pulse Navigation] Error in onStateChange:', error);
      span = undefined;
    }
  };

  const registerNavigationContainer = (
    maybeNavigationContainer: unknown
  ): void => {
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
        return;
      }

      if (isInitialized && navigationContainer === container) {
        console.log('[Pulse Navigation] Container already registered');
        return;
      }

      navigationContainer = container;

      navigationContainer.addListener(
        '__unsafe_action__',
        onNavigationDispatch
      );
      navigationContainer.addListener('state', onStateChange);

      const currentRoute = navigationContainer.getCurrentRoute();
      if (currentRoute) {
        latestRoute = currentRoute;
        pushRecentRouteKey(currentRoute.key);
      }

      isInitialized = true;
      console.log('[Pulse Navigation] Integration initialized successfully');
    } catch (error) {
      console.error('[Pulse Navigation] Error registering container:', error);
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
