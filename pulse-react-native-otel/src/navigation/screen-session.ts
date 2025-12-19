import { Pulse, type Span } from '../index';
import { type AppStateStatus, Platform } from 'react-native';
import { SPAN_NAMES, ATTRIBUTE_KEYS, PULSE_TYPES } from '../pulse.constants';
import type { NavigationRoute, NavigationContainer } from './types';

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
    console.log(
      `[Pulse Navigation] [TEST] Started screen_session span for screen: ${route.name}, routeKey: ${route.key}`
    );
  };

  const endScreenSession = (): void => {
    if (state.screenSessionSpan) {
      const endedKey = state.currentScreenKey;
      state.screenSessionSpan.end();
      console.log(
        `[Pulse Navigation] [TEST] Ended screen_session span for routeKey: ${endedKey}`
      );
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
        console.log(
          `[Pulse Navigation] [TEST] App backgrounded - ending screen_session span for routeKey: ${state.currentScreenKey}`
        );
        endScreenSession();
      }
    } else if (nextAppState === 'active') {
      const currentRoute = navigationContainer?.getCurrentRoute();
      if (currentRoute && !state.screenSessionSpan) {
        console.log(
          `[Pulse Navigation] [TEST] App foregrounded - starting screen_session span for screen: ${currentRoute.name}, routeKey: ${currentRoute.key}`
        );
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