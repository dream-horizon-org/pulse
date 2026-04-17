import { Pulse, type Span } from '../index';
import { getIsStarted } from '../config';
import { Platform } from 'react-native';
import { SPAN_NAMES, ATTRIBUTE_KEYS, PULSE_TYPES } from '../pulse.constants';
import { discardSpan } from '../trace';
import type {
  NavigationRoute,
  NavigationContainer,
} from './navigation.interface';
import { PulseLogger } from '../PulseLogger';

export interface ScreenInteractiveState {
  screenInteractiveSpan: Span | undefined;
  currentInteractiveRouteKey: string | undefined;
}

export const INITIAL_SCREEN_INTERACTIVE_STATE: ScreenInteractiveState = {
  screenInteractiveSpan: undefined,
  currentInteractiveRouteKey: undefined,
};

let globalMarkContentReady: (() => void) | undefined;

export function createScreenInteractiveTracker(
  enabled: boolean,
  state: ScreenInteractiveState,
  navigationContainer: NavigationContainer | undefined
) {
  const discardScreenInteractive = (reason: string): void => {
    if (state.screenInteractiveSpan) {
      PulseLogger.debug(
        `Screen interactive: span discarded — ${reason} (routeKey: ${state.currentInteractiveRouteKey})`
      );
      if (state.screenInteractiveSpan.spanId) {
        discardSpan(state.screenInteractiveSpan.spanId);
      }
      state.screenInteractiveSpan = undefined;
      state.currentInteractiveRouteKey = undefined;
    }
  };

  const startScreenInteractive = (route: NavigationRoute): void => {
    if (!enabled) {
      return;
    }

    if (
      state.screenInteractiveSpan &&
      state.currentInteractiveRouteKey === route.key
    ) {
      return;
    }

    if (state.screenInteractiveSpan) {
      discardScreenInteractive('previous span replaced by new navigation');
    }

    state.screenInteractiveSpan = Pulse.startSpan(
      SPAN_NAMES.SCREEN_INTERACTIVE,
      {
        attributes: {
          [ATTRIBUTE_KEYS.PULSE_TYPE]: PULSE_TYPES.SCREEN_INTERACTIVE,
          [ATTRIBUTE_KEYS.SCREEN_NAME]: route.name,
          [ATTRIBUTE_KEYS.ROUTE_KEY]: route.key,
          [ATTRIBUTE_KEYS.PLATFORM]: Platform.OS,
        },
        inheritContext: false,
      }
    );
    state.currentInteractiveRouteKey = route.key;
    PulseLogger.debug(`Screen interactive: ${route.name} started`);
  };

  const endScreenInteractive = (routeName?: string): void => {
    if (state.screenInteractiveSpan) {
      state.screenInteractiveSpan.end();
      if (routeName) {
        PulseLogger.debug(`Screen interactive: ${routeName} ended`);
      }
      state.screenInteractiveSpan = undefined;
      state.currentInteractiveRouteKey = undefined;
    }
  };

  const handleMarkContentReady = (): void => {
    try {
      if (!enabled) {
        PulseLogger.warn(
          'Screen interactive: markContentReady called but screenInteractiveTracking is disabled'
        );
        return;
      }

      if (!state.screenInteractiveSpan) {
        return;
      }

      const currentRoute = navigationContainer?.getCurrentRoute();
      if (!currentRoute) {
        PulseLogger.warn(
          'Screen interactive: markContentReady called but no current route found'
        );
        discardScreenInteractive('no current route');
        return;
      }

      if (currentRoute.key !== state.currentInteractiveRouteKey) {
        PulseLogger.warn(
          `Screen interactive: markContentReady called for wrong screen. Expected routeKey: ${state.currentInteractiveRouteKey}, Current: ${currentRoute.key}`
        );
        discardScreenInteractive('route key mismatch');
        return;
      }

      endScreenInteractive(currentRoute.name);
    } catch (error) {
      PulseLogger.error(
        `Screen interactive: Error in markContentReady: ${error}`
      );
    }
  };

  globalMarkContentReady = handleMarkContentReady;

  return {
    startScreenInteractive,
    endScreenInteractive,
    discardScreenInteractive,
    markContentReady: handleMarkContentReady,
  };
}

export function markContentReady(): void {
  if (!getIsStarted()) {
    return;
  }
  if (globalMarkContentReady) {
    globalMarkContentReady();
  } else {
    PulseLogger.warn(
      'Navigation: markContentReady called but navigation integration not initialized'
    );
  }
}

export function clearGlobalMarkContentReady(
  markContentReadyFn: () => void
): void {
  if (globalMarkContentReady === markContentReadyFn) {
    globalMarkContentReady = undefined;
  }
}
