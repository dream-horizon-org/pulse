import { startSpan, type Span } from '../trace';
import { type AppStateStatus, Platform } from 'react-native';
import { SPAN_NAMES, ATTRIBUTE_KEYS, PULSE_TYPES } from '../pulse.constants';
import type {
  NavigationRoute,
  NavigationContainer,
} from './navigation.interface';
import { PulseLogger } from '../PulseLogger';
import { LOG_TAGS } from './utils';

export interface ScreenSessionState {
  screenSessionSpan: Span | undefined;
  currentScreenKey: string | undefined;
  /** Route name when the current session span was started (accurate end logs). */
  currentSessionRouteName: string | undefined;
}

export const INITIAL_SCREEN_SESSION_STATE: ScreenSessionState = {
  screenSessionSpan: undefined,
  currentScreenKey: undefined,
  currentSessionRouteName: undefined,
};

export function createScreenSessionTracker(
  enabled: boolean,
  state: ScreenSessionState
) {
  const startScreenSession = (route: NavigationRoute): void => {
    state.screenSessionSpan = startSpan(SPAN_NAMES.SCREEN_SESSION, {
      attributes: {
        [ATTRIBUTE_KEYS.PULSE_TYPE]: PULSE_TYPES.SCREEN_SESSION,
        [ATTRIBUTE_KEYS.SCREEN_NAME]: route.name,
        [ATTRIBUTE_KEYS.ROUTE_KEY]: route.key,
        [ATTRIBUTE_KEYS.PLATFORM]: Platform.OS as 'android' | 'ios',
      },
    });
    state.currentScreenKey = route.key;
    state.currentSessionRouteName = route.name;
    PulseLogger.debug(`${LOG_TAGS.SCREEN_SESSION} ${route.name} started`);
  };

  const endScreenSession = (): void => {
    if (state.screenSessionSpan) {
      const logName = state.currentSessionRouteName;
      state.screenSessionSpan.end();
      if (logName) {
        PulseLogger.debug(`${LOG_TAGS.SCREEN_SESSION} ${logName} ended`);
      }
      state.screenSessionSpan = undefined;
      state.currentScreenKey = undefined;
      state.currentSessionRouteName = undefined;
    }
  };

  /**
   * Keeps one screen-session span aligned with the focused route (same idea as
   * screen interactive: replace when `route.key` changes). Ends the previous
   * session, then starts for the new route.
   */
  const syncSessionToCurrentRoute = (
    route: NavigationRoute,
    appState: AppStateStatus
  ): void => {
    if (!enabled || appState !== 'active') {
      return;
    }

    if (state.screenSessionSpan && state.currentScreenKey === route.key) {
      return;
    }

    if (state.screenSessionSpan) {
      endScreenSession();
    }

    startScreenSession(route);
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
        endScreenSession();
      }
    } else if (nextAppState === 'active') {
      const currentRoute = navigationContainer?.getCurrentRoute();
      if (currentRoute) {
        syncSessionToCurrentRoute(currentRoute, nextAppState);
      }
    }
  };

  return {
    startScreenSession,
    endScreenSession,
    handleAppStateChange,
    syncSessionToCurrentRoute,
  };
}
