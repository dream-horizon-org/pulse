import { Pulse, type Span } from '../index';
import { type AppStateStatus, Platform } from 'react-native';
import { SPAN_NAMES, ATTRIBUTE_KEYS, PULSE_TYPES } from '../pulse.constants';
import type {
  NavigationRoute,
  NavigationContainer,
} from './navigation.interface';
import { LOG_TAGS } from './utils';

export interface ScreenSessionState {
  screenSessionSpan: Span | undefined;
  currentScreenKey: string | undefined;
}

export function createScreenSessionTracker(
  enabled: boolean,
  state: ScreenSessionState
) {
  const startScreenSession = (route: NavigationRoute): void => {
    state.screenSessionSpan = Pulse.startSpan(SPAN_NAMES.SCREEN_SESSION, {
      attributes: {
        [ATTRIBUTE_KEYS.PULSE_TYPE]: PULSE_TYPES.SCREEN_SESSION,
        [ATTRIBUTE_KEYS.SCREEN_NAME]: route.name,
        [ATTRIBUTE_KEYS.ROUTE_KEY]: route.key,
        [ATTRIBUTE_KEYS.PLATFORM]: Platform.OS as 'android' | 'ios',
      },
    });
    state.currentScreenKey = route.key;
    console.log(`${LOG_TAGS.SCREEN_SESSION} ${route.name}`);
  };

  const endScreenSession = (routeName?: string): void => {
    if (state.screenSessionSpan) {
      state.screenSessionSpan.end();
      if (routeName) {
        console.log(`${LOG_TAGS.SCREEN_SESSION} ${routeName} ended`);
      }
      state.screenSessionSpan = undefined;
      state.currentScreenKey = undefined;
    }
  };

  const handleAppStateChange = (
    nextAppState: AppStateStatus,
    navigationContainer: NavigationContainer | undefined
  ): void => {
    if (!enabled) {
      return;
    }

    if (nextAppState === 'background' || nextAppState === 'inactive') {
      if (state.screenSessionSpan) {
        const currentRoute = navigationContainer?.getCurrentRoute();
        endScreenSession(currentRoute?.name);
      }
    } else if (nextAppState === 'active') {
      const currentRoute = navigationContainer?.getCurrentRoute();
      if (currentRoute && !state.screenSessionSpan) {
        startScreenSession(currentRoute);
      }
    }
  };

  const shouldStartSession = (
    currentRoute: NavigationRoute,
    appState: AppStateStatus
  ): boolean => {
    return (
      enabled &&
      appState === 'active' &&
      !state.screenSessionSpan &&
      state.currentScreenKey !== currentRoute.key
    );
  };

  return {
    startScreenSession,
    endScreenSession,
    handleAppStateChange,
    shouldStartSession,
  };
}
