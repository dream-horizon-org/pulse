import { Pulse, type Span } from '../index';
import { Platform } from 'react-native';
import {
  SPAN_NAMES,
  ATTRIBUTE_KEYS,
  PULSE_TYPES,
  PHASE_VALUES,
} from '../pulse.constants';
import type { NavigationRoute } from './navigation.interface';
import { LOG_TAGS } from './utils';

export interface ScreenLoadState {
  navigationSpan: Span | undefined;
  latestRoute: NavigationRoute | undefined;
}

export const INITIAL_SCREEN_LOAD_STATE: ScreenLoadState = {
  navigationSpan: undefined,
  latestRoute: undefined,
};

export function createScreenLoadTracker(
  enabled: boolean,
  state: ScreenLoadState,
  getRecentRouteKeys: () => string[],
  pushRecentRouteKey: (key: string) => void,
  onLoadEnd?: (route: NavigationRoute) => void
) {
  const startNavigationSpan = (): void => {
    if (!enabled) {
      return;
    }

    state.navigationSpan = Pulse.startSpan(SPAN_NAMES.NAVIGATED, {
      attributes: {
        [ATTRIBUTE_KEYS.PULSE_TYPE]: PULSE_TYPES.SCREEN_LOAD,
        [ATTRIBUTE_KEYS.PHASE]: PHASE_VALUES.START,
        [ATTRIBUTE_KEYS.PLATFORM]: Platform.OS as 'android' | 'ios',
      },
    });
    console.log(`${LOG_TAGS.SCREEN_LOAD} started`);
  };

  const endNavigationSpan = (
    currentRoute?: NavigationRoute,
    previousRoute?: NavigationRoute,
    routeHasBeenSeen?: boolean
  ): void => {
    if (state.navigationSpan) {
      const route = currentRoute || state.latestRoute;

      if (route) {
        const hasBeenSeen =
          routeHasBeenSeen !== undefined
            ? routeHasBeenSeen
            : getRecentRouteKeys().includes(route.key);

        state.navigationSpan.setAttributes({
          [ATTRIBUTE_KEYS.SCREEN_NAME]: route.name,
          [ATTRIBUTE_KEYS.LAST_SCREEN_NAME]: previousRoute?.name || undefined,
          [ATTRIBUTE_KEYS.ROUTE_HAS_BEEN_SEEN]: hasBeenSeen,
          [ATTRIBUTE_KEYS.ROUTE_KEY]: route.key,
        });
      }

      state.navigationSpan.end();
      state.navigationSpan = undefined;

      if (route) {
        console.log(`${LOG_TAGS.SCREEN_LOAD} ${route.name} ended`);
        if (onLoadEnd) {
          onLoadEnd(route);
        }
      }
    }
  };

  const handleStateChange = (currentRoute: NavigationRoute): void => {
    if (!enabled || !state.navigationSpan) {
      return;
    }

    const previousRoute = state.latestRoute;

    if (previousRoute && previousRoute.key === currentRoute.key) {
      const routeHasBeenSeen = getRecentRouteKeys().includes(currentRoute.key);
      endNavigationSpan(currentRoute, previousRoute, routeHasBeenSeen);
      return;
    }

    const routeHasBeenSeen = getRecentRouteKeys().includes(currentRoute.key);
    state.latestRoute = currentRoute;
    pushRecentRouteKey(currentRoute.key);

    endNavigationSpan(currentRoute, previousRoute, routeHasBeenSeen);
  };

  return {
    startNavigationSpan,
    endNavigationSpan,
    handleStateChange,
  };
}
