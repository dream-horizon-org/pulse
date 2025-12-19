import { Pulse, type Span } from '../index';
import { Platform } from 'react-native';
import {
  SPAN_NAMES,
  ATTRIBUTE_KEYS,
  PULSE_TYPES,
  PHASE_VALUES,
} from '../pulse.constants';
import type { NavigationRoute } from './types';
import { LOG_TAGS } from './utils';

export interface ScreenLoadState {
  navigationSpan: Span | undefined;
  latestRoute: NavigationRoute | undefined;
}

export function createScreenLoadTracker(
  enabled: boolean,
  state: ScreenLoadState,
  recentRouteKeys: string[],
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

  const endNavigationSpan = (): void => {
    if (state.navigationSpan) {
      const route = state.latestRoute;
      state.navigationSpan.end();
      state.navigationSpan = undefined;

      if (route) {
        console.log(`${LOG_TAGS.SCREEN_LOAD} ${route.name}`);
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
      endNavigationSpan();
      return;
    }

    state.latestRoute = currentRoute;
    const routeHasBeenSeen = recentRouteKeys.includes(currentRoute.key);
    pushRecentRouteKey(currentRoute.key);

    state.navigationSpan.setAttributes({
      [ATTRIBUTE_KEYS.SCREEN_NAME]: currentRoute.name,
      [ATTRIBUTE_KEYS.LAST_SCREEN_NAME]: previousRoute?.name || undefined,
      [ATTRIBUTE_KEYS.ROUTE_HAS_BEEN_SEEN]: routeHasBeenSeen,
      [ATTRIBUTE_KEYS.ROUTE_KEY]: currentRoute.key,
    });

    endNavigationSpan();
  };

  return {
    startNavigationSpan,
    endNavigationSpan,
    handleStateChange,
  };
}
